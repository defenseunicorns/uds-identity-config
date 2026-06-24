/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.AbstractJWTClientValidator;
import org.keycloak.authentication.authenticators.client.FederatedJWTClientValidator;
import org.keycloak.broker.provider.ClientAssertionIdentityProvider;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.SignatureProvider;
import org.keycloak.jose.jws.JWSHeader;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.keys.PublicKeyStorageProvider;
import org.keycloak.keys.PublicKeyStorageUtils;
import org.keycloak.models.KeycloakSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

/**
 * UDS Kubernetes client-assertion identity provider. Verifies federated Kubernetes service-account JWTs like the
 * stock provider, with two differences for managed/external-issuer environments:
 * <ul>
 *   <li>signing keys are loaded via {@link UDSKubernetesJwksEndpointLoader}, which does NOT forward the pod
 *       service-account token to external/public discovery or JWKS endpoints; and</li>
 *   <li>the expected issuer is discovered from the cluster and persisted onto the IdP at validation time
 *       ({@code automaticIssuerDiscovery}; see {@link UDSKubernetesIdentityProviderConfig#validate}).</li>
 * </ul>
 *
 * <p><b>WORKAROUND:</b> this entire provider is a temporary bridge. Once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> ships
 * destination-based token forwarding and managed-issuer discovery in the Keycloak version UDS runs, delete this
 * plugin and switch the realm IdP back to the stock {@code providerId: "kubernetes"}.
 */
public class UDSKubernetesIdentityProvider implements ClientAssertionIdentityProvider<UDSKubernetesIdentityProviderConfig> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final KeycloakSession session;
    private final UDSKubernetesIdentityProviderConfig config;

    public UDSKubernetesIdentityProvider(KeycloakSession session, UDSKubernetesIdentityProviderConfig config) {
        this.session = session;
        this.config = config;
    }

    @Override
    public boolean verifyClientAssertion(ClientAuthenticationFlowContext context) throws Exception {
        // The issuer was resolved and persisted at IdP validation time (see UDSKubernetesIdentityProviderConfig),
        // so it is read straight from config here.
        String expectedIssuer = config.getIssuer();
        if (expectedIssuer == null || expectedIssuer.isBlank()) {
            logger.warn("Kubernetes IdP has no resolved issuer; rejecting assertion");
            return false; // fail closed
        }

        FederatedJWTClientValidator validator = new FederatedJWTClientValidator(
                context, this::verifySignature, expectedIssuer, config.getAllowedClockSkew(), true);
        int maxExp = config.getFederatedClientAssertionMaxExpiration();
        validator.setMaximumExpirationTime(maxExp != 0 ? maxExp : 3600); // Kubernetes defaults to 1h
        return validator.validate();
    }

    /**
     * Verifies the assertion signature. Copied from Keycloak's KubernetesIdentityProvider.verifySignature (which is
     * private upstream); it differs only in using {@link UDSKubernetesJwksEndpointLoader} built from the resolved
     * {@code getIssuer()}, which fetches keys from the issuer's own (public) endpoint and attaches the pod token
     * only to the in-cluster API server.
     * Remove when <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> is resolved.
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
                    new UDSKubernetesJwksEndpointLoader(session, config.getIssuer()));

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

    @Override
    public UDSKubernetesIdentityProviderConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
    }
}
