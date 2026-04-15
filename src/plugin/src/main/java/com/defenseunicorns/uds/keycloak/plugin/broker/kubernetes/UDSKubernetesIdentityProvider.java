/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.AbstractJWTClientValidator;
import org.keycloak.broker.kubernetes.KubernetesIdentityProvider;
import org.keycloak.broker.kubernetes.KubernetesIdentityProviderConfig;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.SignatureProvider;
import org.keycloak.jose.jws.JWSHeader;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.keys.PublicKeyStorageProvider;
import org.keycloak.keys.PublicKeyStorageUtils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.util.JsonSerialization;

import com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client.UDSFederatedJWTClientValidator;

import org.keycloak.http.simple.SimpleHttp;

import org.jboss.logging.Logger;

/**
 * Custom Kubernetes Identity Provider that discovers the real OIDC issuer at runtime.
 *
 * <h2>Why this exists</h2>
 *
 * The upstream {@link KubernetesIdentityProvider} has its {@code verifyClientAssertion} method
 * tightly coupled to the configured issuer URL. On managed Kubernetes clusters (EKS, AKS, GKE),
 * three things break:
 *
 * <ol>
 *   <li><b>Issuer validation fails:</b> The upstream validator compares the JWT's issuer claim
 *       against the configured IdP issuer. On EKS, the JWT issuer is a cloud-specific URL
 *       (e.g. {@code https://oidc.eks.us-gov-west-1.amazonaws.com/id/...}) while the configured
 *       issuer is {@code https://kubernetes.default.svc.cluster.local}.</li>
 *
 *   <li><b>OIDC discovery requires authentication:</b> On managed clusters, the API server's
 *       {@code /.well-known/openid-configuration} endpoint returns 401 Unauthorized unless
 *       a valid service account token is presented. The upstream code does not always include
 *       the SA token (see {@link UDSKubernetesJwksEndpointLoader} for details).</li>
 *
 *   <li><b>JWKS loading fails:</b> The upstream {@code KubernetesJwksEndpointLoader} only
 *       includes the SA token when its issuer matches the configured issuer. On managed clusters
 *       they don't match, so the token is excluded, and JWKS requests fail with 401.</li>
 * </ol>
 *
 * <h2>How this provider fixes it</h2>
 *
 * <ul>
 *   <li>{@link #discoverIssuer} calls the K8s API server's OIDC discovery endpoint
 *       (with the SA token for auth) and extracts the real issuer URL. This discovered issuer
 *       is passed to the validator so the issuer check passes naturally.</li>
 *   <li>{@link #verifySignature(AbstractJWTClientValidator)} uses {@link UDSKubernetesJwksEndpointLoader} which always
 *       attaches the SA token, so JWKS loading works on managed clusters.</li>
 *   <li>The discovered issuer is cached in a static {@link ConcurrentHashMap} because the
 *       OIDC issuer URL never changes during a cluster's lifetime.</li>
 * </ul>
 *
 * <h2>Why verifySignature is copied</h2>
 *
 * The upstream {@code KubernetesIdentityProvider.verifySignature()} is {@code private},
 * so it cannot be overridden or called via {@code super}. We copy it here and swap in
 * {@link UDSKubernetesJwksEndpointLoader} to fix the SA token inclusion issue.
 *
 * @see UDSKubernetesJwksEndpointLoader
 * @see UDSFederatedJWTClientValidator
 * @see com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client.UDSClientAssertionStrategy
 */
public class UDSKubernetesIdentityProvider extends KubernetesIdentityProvider {

    private static final Logger LOGGER = Logger.getLogger(UDSKubernetesIdentityProvider.class);

    // Static cache: OIDC issuer URL never changes during a cluster's lifetime.
    // Keyed by configured issuer URL to support multiple IdP instances.
    private static final ConcurrentHashMap<String, String> ISSUER_CACHE = new ConcurrentHashMap<>();

    private final KeycloakSession session;
    private final KubernetesIdentityProviderConfig config;

    public UDSKubernetesIdentityProvider(KeycloakSession session, KubernetesIdentityProviderConfig config) {
        super(session, config);
        this.session = session;
        this.config = config;
    }

    @Override
    public boolean verifyClientAssertion(ClientAuthenticationFlowContext context) throws Exception {
        String discoveredIssuer = discoverIssuer(config.getIssuer());

        UDSFederatedJWTClientValidator validator = new UDSFederatedJWTClientValidator(
            context, this::verifySignature, discoveredIssuer, config.getAllowedClockSkew(), true
        );
        validator.setMaximumExpirationTime(3600);
        return validator.validate();
    }

    /**
     * Discovers the real OIDC issuer from the Kubernetes API server's OIDC discovery endpoint.
     *
     * <p>On k3d/self-hosted clusters: returns the same configured issuer (no-op).
     * On EKS/AKS/GKE: returns the managed cluster's public OIDC issuer URL.
     *
     * <p>The SA token is included in the request because managed K8s API servers (notably EKS)
     * return 401 Unauthorized for unauthenticated requests to the OIDC discovery endpoint
     * when accessed via the internal {@code kubernetes.default.svc.cluster.local} URL.
     *
     * @param kubeApiServerUrl the configured K8s API server URL (e.g. https://kubernetes.default.svc.cluster.local)
     * @return the discovered issuer URL, or {@code kubeApiServerUrl} as fallback on any failure
     */
    String discoverIssuer(String kubeApiServerUrl) {
        String cached = ISSUER_CACHE.get(kubeApiServerUrl);
        if (cached != null) {
            LOGGER.debugf("Using cached OIDC issuer '%s' for K8s API server '%s'", cached, kubeApiServerUrl);
            return cached;
        }

        String discovered = fetchIssuerFromOidc(kubeApiServerUrl);
        // putIfAbsent is atomic -- if another thread raced us, use their result
        String existing = ISSUER_CACHE.putIfAbsent(kubeApiServerUrl, discovered);
        return existing != null ? existing : discovered;
    }

    private String fetchIssuerFromOidc(String kubeApiServerUrl) {
        String wellKnownEndpoint = kubeApiServerUrl + "/.well-known/openid-configuration";
        try {
            String saToken = KubernetesUtils.readServiceAccountToken();

            SimpleHttp simpleHttp = SimpleHttp.create(session);
            var request = simpleHttp.doGet(wellKnownEndpoint).acceptJson();
            if (saToken != null) {
                request.header("Authorization", "Bearer " + saToken);
            }

            OIDCConfigurationRepresentation oidcConfig = JsonSerialization.readValue(
                request.asString(), OIDCConfigurationRepresentation.class);
            String discoveredIssuer = oidcConfig.getIssuer();

            if (discoveredIssuer == null || discoveredIssuer.isEmpty()) {
                LOGGER.errorf("OIDC discovery from '%s' returned empty issuer, falling back to '%s'",
                    wellKnownEndpoint, kubeApiServerUrl);
                return kubeApiServerUrl;
            }

            if (!discoveredIssuer.startsWith("https://")) {
                LOGGER.errorf("OIDC discovery from '%s' returned non-HTTPS issuer '%s', falling back to '%s'",
                    wellKnownEndpoint, discoveredIssuer, kubeApiServerUrl);
                return kubeApiServerUrl;
            }

            LOGGER.debugf("Discovered OIDC issuer '%s' for K8s API server '%s'", discoveredIssuer, kubeApiServerUrl);
            return discoveredIssuer;
        } catch (Exception e) {
            LOGGER.errorf(e, "Failed to discover OIDC issuer from '%s', falling back to '%s'",
                wellKnownEndpoint, kubeApiServerUrl);
            return kubeApiServerUrl;
        }
    }

    /**
     * Verifies the JWT signature using JWKS from the Kubernetes API server.
     *
     * <p>Copied from {@code KubernetesIdentityProvider.verifySignature()} because that method
     * is {@code private} and cannot be overridden. The only change is using
     * {@link UDSKubernetesJwksEndpointLoader} instead of the upstream
     * {@code KubernetesJwksEndpointLoader} to ensure the SA token is always included
     * in JWKS requests (the upstream loader skips the token when the issuer doesn't match).
     */
    private boolean verifySignature(AbstractJWTClientValidator validator) {
        try {
            JWSInput jws = validator.getState().getJws();
            JWSHeader header = jws.getHeader();
            String kid = header.getKeyId();
            String alg = header.getRawAlgorithm();

            String modelKey = PublicKeyStorageUtils.getIdpModelCacheKey(
                validator.getContext().getRealm().getId(), config.getInternalId());
            PublicKeyStorageProvider keyStorage = session.getProvider(PublicKeyStorageProvider.class);
            KeyWrapper publicKey = keyStorage.getPublicKey(modelKey, kid, alg,
                new UDSKubernetesJwksEndpointLoader(session, config.getIssuer()));
            if (publicKey == null) {
                LOGGER.warnf("Public key not found for kid='%s', alg='%s'", kid, alg);
                return false;
            }

            SignatureProvider signatureProvider = session.getProvider(SignatureProvider.class, alg);
            if (signatureProvider == null) {
                LOGGER.debugf("Signature provider not found for algorithm '%s'", alg);
                return false;
            }

            return signatureProvider.verifier(publicKey)
                .verify(jws.getEncodedSignatureInput().getBytes(StandardCharsets.UTF_8), jws.getSignature());
        } catch (Exception e) {
            LOGGER.debug("Failed to verify token signature", e);
            return false;
        }
    }

    // Visible for testing
    static void clearIssuerCache() {
        ISSUER_CACHE.clear();
    }
}
