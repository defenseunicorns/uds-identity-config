/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client.UDSFederatedJWTClientValidator;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.AbstractJWTClientValidator;
import org.keycloak.broker.provider.ClientAssertionIdentityProvider;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.SignatureProvider;
import org.keycloak.jose.jws.JWSHeader;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.keys.PublicKeyStorageProvider;
import org.keycloak.keys.PublicKeyStorageUtils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDS Kubernetes client-assertion identity provider. Verifies federated Kubernetes service-account JWTs like the
 * stock provider, with two differences for managed/external-issuer environments:
 * <ul>
 *   <li>signing keys are loaded via {@link UDSKubernetesJwksEndpointLoader}, which does NOT forward the pod
 *       service-account token to external/public discovery or JWKS endpoints; and</li>
 *   <li>the expected issuer may be discovered at validation time from the in-cluster API
 *       ({@code automaticIssuerDiscovery}).</li>
 * </ul>
 * It also uses {@link UDSFederatedJWTClientValidator} to backport the keycloak/keycloak#48026 client_id fix.
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> this entire provider is a temporary bridge. Once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> ships
 * destination-based token forwarding (plus managed-issuer discovery and keycloak#48026) in the Keycloak version
 * UDS runs, delete this plugin and switch the realm IdP back to the stock {@code providerId: "kubernetes"}.
 */
public class UDSKubernetesIdentityProvider implements ClientAssertionIdentityProvider<UDSKubernetesIdentityProviderConfig> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /** Discovered issuer cache, keyed by discovery URL. Kubernetes issuers are stable. */
    private static final Map<String, String> DISCOVERED_ISSUERS = new ConcurrentHashMap<>();

    private final KeycloakSession session;
    private final UDSKubernetesIdentityProviderConfig config;

    public UDSKubernetesIdentityProvider(KeycloakSession session, UDSKubernetesIdentityProviderConfig config) {
        this.session = session;
        this.config = config;
    }

    @Override
    public boolean verifyClientAssertion(ClientAuthenticationFlowContext context) throws Exception {
        String expectedIssuer = config.getIssuer();
        if (config.isAutomaticIssuerDiscovery()) {
            expectedIssuer = discoverIssuer();
            if (expectedIssuer == null) {
                logger.warn("Automatic issuer discovery enabled but issuer could not be resolved; rejecting assertion");
                return false; // fail closed
            }
        }

        UDSFederatedJWTClientValidator validator = new UDSFederatedJWTClientValidator(
                context, this::verifySignature, expectedIssuer, config.getAllowedClockSkew(), true);
        int maxExp = config.getFederatedClientAssertionMaxExpiration();
        validator.setMaximumExpirationTime(maxExp != 0 ? maxExp : 3600); // Kubernetes defaults to 1h
        return validator.validate();
    }

    /**
     * Copied verbatim (the method is private upstream) from Keycloak 26.6.3 KubernetesIdentityProvider.verifySignature,
     * swapping only the JWKS loader for {@link UDSKubernetesJwksEndpointLoader}.
     * Source: https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/broker/kubernetes/KubernetesIdentityProvider.java#L42-L66
     * Remove when keycloak#49039 is resolved.
     */
    private boolean verifySignature(AbstractJWTClientValidator validator) {
        try {
            JWSInput jws = validator.getState().getJws();
            JWSHeader header = jws.getHeader();
            String kid = header.getKeyId();
            String alg = header.getRawAlgorithm();

            String modelKey = PublicKeyStorageUtils.getIdpModelCacheKey(validator.getContext().getRealm().getId(), config.getInternalId());
            PublicKeyStorageProvider keyStorage = session.getProvider(PublicKeyStorageProvider.class);
            KeyWrapper publicKey = keyStorage.getPublicKey(modelKey, kid, alg,
                    new UDSKubernetesJwksEndpointLoader(session, config.getIssuerDiscoveryUrl(), config.getJwksAuthMode()));

            SignatureProvider signatureProvider = session.getProvider(SignatureProvider.class, alg);
            if (signatureProvider == null) {
                logger.debug("Failed to verify token, signature provider not found for algorithm {}", alg);
                return false;
            }

            return signatureProvider.verifier(publicKey)
                    .verify(jws.getEncodedSignatureInput().getBytes(StandardCharsets.UTF_8), jws.getSignature());
        } catch (Exception e) {
            logger.debug("Failed to verify token signature", e);
            return false;
        }
    }

    private String discoverIssuer() {
        String discoveryUrl = config.getIssuerDiscoveryUrl();
        String cached = DISCOVERED_ISSUERS.get(discoveryUrl);
        if (cached != null) {
            return cached;
        }
        String issuer = fetchIssuer(discoveryUrl);
        if (issuer != null) {
            DISCOVERED_ISSUERS.put(discoveryUrl, issuer);
        }
        return issuer;
    }

    private String fetchIssuer(String discoveryUrl) {
        String wellKnown = KubernetesUtils.wellKnownUrl(discoveryUrl);
        try {
            OIDCConfigurationRepresentation discovery =
                    KubernetesUtils.fetchJson(session, wellKnown, "application/json", OIDCConfigurationRepresentation.class, config.getJwksAuthMode());
            String issuer = discovery != null ? discovery.getIssuer() : null;
            if (issuer == null || issuer.isBlank() || !issuer.startsWith("https://")) {
                logger.warn("Discovered issuer is missing or not HTTPS from {}", wellKnown);
                return null;
            }
            return issuer;
        } catch (Exception e) {
            logger.debug("Issuer discovery failed for {}", wellKnown, e);
            return null;
        }
    }

    @Override
    public UDSKubernetesIdentityProviderConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
    }
}
