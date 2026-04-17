/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.keys.PublicKeyLoader;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.util.JWKSUtils;

import org.apache.http.HttpHeaders;
import org.jboss.logging.Logger;

/**
 * JWKS endpoint loader that always includes the service account token in requests.
 *
 * <h2>Why this exists</h2>
 *
 * The upstream {@code KubernetesJwksEndpointLoader.getToken()} only includes the mounted
 * service account token when its {@code iss} claim matches the configured IdP issuer:
 * <pre>
 * if (jwt.getIssuer().equals(issuer)) {
 *     return token;  // included
 * } else {
 *     return null;   // excluded — "issuer mismatch"
 * }
 * </pre>
 *
 * On managed Kubernetes clusters (EKS, AKS, GKE), the SA token issuer is a cloud-specific URL
 * (e.g. {@code https://oidc.eks.us-gov-west-1.amazonaws.com/id/...}) while the configured issuer
 * is {@code https://kubernetes.default.svc.cluster.local}. These never match, so the upstream
 * loader always excludes the token. Without the token, the K8s API server returns 401 for both
 * OIDC discovery and JWKS requests, causing signature verification to fail with a
 * {@code NullPointerException} (null response body).
 *
 * <h2>What this loader changes</h2>
 *
 * The only difference from upstream is that {@link KubernetesUtils#readServiceAccountToken()}
 * always returns the SA token if the file exists, without checking the issuer claim. The SA
 * token is only forwarded to the JWKS endpoint when the token's {@code iss} claim matches the
 * {@code issuer} field from the OIDC discovery response. This ensures the token is only sent
 * to endpoints belonging to the same authority that issued it.
 *
 * @see UDSKubernetesIdentityProvider
 */
public class UDSKubernetesJwksEndpointLoader implements PublicKeyLoader {

    private static final Logger LOGGER = Logger.getLogger(UDSKubernetesJwksEndpointLoader.class);

    private final KeycloakSession session;
    private final String issuer;

    public UDSKubernetesJwksEndpointLoader(KeycloakSession session, String issuer) {
        this.session = session;
        this.issuer = issuer;
    }

    /**
     * Loads JWKS public keys from the Kubernetes OIDC endpoint.
     *
     * <p>Identical to upstream except it uses {@link KubernetesUtils#readServiceAccountToken()}
     * which always includes the SA token regardless of issuer match.
     */
    @Override
    public PublicKeysWrapper loadKeys() throws Exception {
        SimpleHttp simpleHttp = SimpleHttp.create(session);
        String token = KubernetesUtils.readServiceAccountToken();

        String wellKnownEndpoint = issuer + "/.well-known/openid-configuration";

        SimpleHttpRequest wellKnownRequest = simpleHttp.doGet(wellKnownEndpoint).acceptJson();
        if (token != null) {
            wellKnownRequest.auth(token);
        }

        OIDCConfigurationRepresentation oidcConfig = KubernetesUtils.executeAndParse(wellKnownRequest,
            wellKnownEndpoint, OIDCConfigurationRepresentation.class);

        String jwksUri = oidcConfig.getJwksUri();
        if (jwksUri == null || jwksUri.isEmpty()) {
            throw new IllegalStateException("OIDC discovery at " + wellKnownEndpoint + " returned no jwks_uri");
        }

        SimpleHttpRequest jwksRequest = simpleHttp.doGet(jwksUri).header(HttpHeaders.ACCEPT, "application/jwk-set+json");
        if (token != null && shouldIncludeToken(token, oidcConfig.getIssuer())) {
            jwksRequest.auth(token);
        }

        JSONWebKeySet jwks = KubernetesUtils.executeAndParse(jwksRequest, jwksUri, JSONWebKeySet.class);
        return JWKSUtils.getKeyWrappersForUse(jwks, JWK.Use.SIG);
    }

    /**
     * Determines whether the SA token should be included in the JWKS request by comparing
     * the token's {@code iss} claim against the OIDC discovery {@code issuer} field.
     *
     * <p>The OIDC discovery response includes both the {@code issuer} and the {@code jwks_uri}.
     * When the SA token's {@code iss} matches the discovered {@code issuer}, the token belongs
     * to the same authority that advertises the JWKS endpoint, so it is safe to forward.
     * This works across all Kubernetes distributions:
     * <ul>
     *   <li>k3d/vanilla: both are {@code https://kubernetes.default.svc.cluster.local}</li>
     *   <li>EKS: both are {@code https://oidc.eks.*.amazonaws.com/id/...}</li>
     * </ul>
     */
    static boolean shouldIncludeToken(String token, String discoveredIssuer) {
        if (discoveredIssuer == null) {
            LOGGER.debug("OIDC discovery returned no issuer, skipping auth for JWKS request");
            return false;
        }

        try {
            JWSInput jws = new JWSInput(token);
            JsonWebToken jwt = jws.readJsonContent(JsonWebToken.class);
            String tokenIssuer = jwt.getIssuer();
            if (tokenIssuer == null) {
                LOGGER.debug("SA token has no issuer claim, skipping auth for JWKS request");
                return false;
            }

            boolean match = tokenIssuer.equals(discoveredIssuer);
            LOGGER.debugf("SA token issuer '%s', discovered issuer '%s', including token: %s",
                tokenIssuer, discoveredIssuer, match);
            return match;
        } catch (Exception e) {
            LOGGER.debug("Failed to parse SA token to extract issuer, skipping auth for JWKS request", e);
            return false;
        }
    }
}
