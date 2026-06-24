/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.keys.PublicKeyLoader;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.util.JWKSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Loads signing keys for the UDS Kubernetes provider WITHOUT the stock provider's unconditional pod-token
 * forwarding. Discovery (and the JWKS it points to) is fetched from the provider's resolved {@code issuer} — the
 * issuer in the token, which is the cluster's public OIDC endpoint — so a public JWKS (S3/EKS/AKS OIDC) is fetched
 * anonymously, while only the in-cluster Kubernetes API server gets the pod service-account token.
 *
 * <p><b>WORKAROUND:</b> delete once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> lets the stock
 * provider control whether the service-account token is forwarded.
 */
public class UDSKubernetesJwksEndpointLoader implements PublicKeyLoader {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final KeycloakSession session;
    private final String issuer;

    public UDSKubernetesJwksEndpointLoader(KeycloakSession session, String issuer) {
        this.session = session;
        this.issuer = issuer;
    }

    @Override
    public PublicKeysWrapper loadKeys() throws Exception {
        String token = getToken(issuer);

        String wellKnown = KubernetesUtils.wellKnownUrl(issuer);
        OIDCConfigurationRepresentation discovery = KubernetesUtils.fetchJson(session, wellKnown, "application/json",
                OIDCConfigurationRepresentation.class,
                KubernetesUtils.isTrustedKubernetesApiUrl(issuer) ? token : null);

        String jwksUri = discovery != null ? discovery.getJwksUri() : null;
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("OIDC discovery document missing jwks_uri: " + wellKnown);
        }

        JSONWebKeySet jwks = KubernetesUtils.fetchJson(session, jwksUri, "application/jwk-set+json",
                JSONWebKeySet.class,
                KubernetesUtils.isTrustedKubernetesApiJwksUrl(jwksUri, issuer) ? token : null);
        if (jwks == null || jwks.getKeys() == null) {
            throw new IllegalStateException("JWKS document had no keys: " + jwksUri);
        }

        return JWKSUtils.getKeyWrappersForUse(jwks, JWK.Use.SIG);
    }

    /**
     * The mounted pod service-account token, but only if its own issuer matches the issuer we're loading keys for
     * (so the token is never forwarded to an issuer it doesn't belong to). Copied from Keycloak 26.6.3
     * <a href="https://github.com/keycloak/keycloak/blob/8a67e82f8c85b35bbe69dbc9f3cd28aa26c00ebb/services/src/main/java/org/keycloak/broker/kubernetes/KubernetesJwksEndpointLoader.java#L59-L78">KubernetesJwksEndpointLoader#getToken</a>,
     * reading the token via {@link KubernetesUtils#readServiceAccountToken}.
     */
    private String getToken(String issuer) {
        String token = KubernetesUtils.readServiceAccountToken();
        if (token == null) {
            return null;
        }
        try {
            JsonWebToken jwt = new JWSInput(token).readJsonContent(JsonWebToken.class);
            if (issuer.equals(jwt.getIssuer())) {
                logger.trace("Including service account token in request");
                return token;
            }
            logger.debug("Not including service account token due to issuer mismatch");
        } catch (Exception e) {
            logger.warn("Failed to parse service account token", e);
        }
        return null;
    }
}
