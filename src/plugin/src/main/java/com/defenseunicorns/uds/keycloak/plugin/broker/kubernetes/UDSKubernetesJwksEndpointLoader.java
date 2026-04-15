/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.keys.PublicKeyLoader;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.util.JWKSUtils;

import org.apache.http.HttpHeaders;

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

        // Forwarding a token along with the request is safe here. The Kubernetes API Server issuer is configured at the
        // bootstrap time with the `--oidc-*` flags. So a successful attack would require controling these flags
        // and if that really happens, it's game over already.
        // So essentially - we're not sacrificing any security here.
        SimpleHttpRequest jwksRequest = simpleHttp.doGet(jwksUri).header(HttpHeaders.ACCEPT, "application/jwk-set+json");
        if (token != null) {
            jwksRequest.auth(token);
        }

        JSONWebKeySet jwks = jwksRequest.asJson(JSONWebKeySet.class);
        return JWKSUtils.getKeyWrappersForUse(jwks, JWK.Use.SIG);
    }
}
