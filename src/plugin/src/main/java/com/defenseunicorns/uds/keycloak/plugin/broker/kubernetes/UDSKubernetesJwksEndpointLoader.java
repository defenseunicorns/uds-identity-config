/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.common.util.UriUtils;
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
 * token is safe to include unconditionally because it's already scoped to the cluster and is
 * the same token the upstream code would include if the issuer matched.
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
        String jwksUri = wellKnownRequest.asJson(OIDCConfigurationRepresentation.class).getJwksUri();
        if (jwksUri == null || jwksUri.isEmpty()) {
            throw new IllegalStateException("OIDC discovery at " + wellKnownEndpoint + " returned no jwks_uri");
        }

        SimpleHttpRequest jwksRequest = simpleHttp.doGet(jwksUri).header(HttpHeaders.ACCEPT, "application/jwk-set+json");
        if (token != null && shouldIncludeToken(token, jwksUri)) {
            jwksRequest.auth(token);
        }

        JSONWebKeySet jwks = jwksRequest.asJson(JSONWebKeySet.class);
        return JWKSUtils.getKeyWrappersForUse(jwks, JWK.Use.SIG);
    }

    /**
     * Determines whether the SA token should be included in the JWKS request by comparing
     * the token's {@code iss} claim origin against the JWKS URI origin.
     *
     * <p>On vanilla Kubernetes both origins are {@code https://kubernetes.default.svc.cluster.local}.
     * On managed clusters (EKS, AKS, GKE) both origins are the cloud OIDC endpoint
     * (e.g. {@code https://oidc.eks.*.amazonaws.com}). In either case the token is only sent
     * to an endpoint belonging to the same authority that issued it.
     */
    static boolean shouldIncludeToken(String token, String jwksUri) {
        try {
            JWSInput jws = new JWSInput(token);
            JsonWebToken jwt = jws.readJsonContent(JsonWebToken.class);
            String tokenIssuer = jwt.getIssuer();
            if (tokenIssuer == null) {
                LOGGER.debug("SA token has no issuer claim, skipping auth for JWKS request");
                return false;
            }

            String tokenOrigin = UriUtils.getOrigin(tokenIssuer);
            String jwksOrigin = UriUtils.getOrigin(jwksUri);
            LOGGER.debugf("SA token issuer origin '%s', JWKS URI origin '%s'", tokenOrigin, jwksOrigin);
            return tokenOrigin != null && tokenOrigin.equals(jwksOrigin);
        } catch (Exception e) {
            LOGGER.debug("Failed to parse SA token to extract issuer, skipping auth for JWKS request", e);
            return false;
        }
    }
}
