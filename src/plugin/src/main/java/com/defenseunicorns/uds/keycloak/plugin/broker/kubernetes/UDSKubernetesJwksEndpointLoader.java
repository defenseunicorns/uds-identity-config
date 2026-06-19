/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.keys.PublicKeyLoader;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.util.JWKSUtils;

/**
 * Loads signing keys for the UDS Kubernetes provider WITHOUT the stock provider's unconditional pod-token
 * forwarding. Both the discovery and JWKS fetches go through {@link KubernetesUtils#fetchJson}, which is
 * anonymous-first: the pod service-account token is attached only if the endpoint answers with a 401/403
 * challenge (per {@link UDSKubernetesHttpAuthPolicy}). So a public JWKS (S3/EKS/AKS OIDC) is fetched anonymously,
 * while the in-cluster apiserver JWKS — which requires auth — gets the token.
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> delete once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> lets the stock
 * provider control whether the service-account token is forwarded.
 */
public class UDSKubernetesJwksEndpointLoader implements PublicKeyLoader {

    private final KeycloakSession session;
    private final String discoveryBaseUrl;
    private final UDSKubernetesHttpAuthPolicy.Mode authMode;

    public UDSKubernetesJwksEndpointLoader(KeycloakSession session, String discoveryBaseUrl,
                                           UDSKubernetesHttpAuthPolicy.Mode authMode) {
        this.session = session;
        this.discoveryBaseUrl = discoveryBaseUrl;
        this.authMode = authMode;
    }

    @Override
    public PublicKeysWrapper loadKeys() throws Exception {
        String wellKnown = KubernetesUtils.wellKnownUrl(discoveryBaseUrl);
        OIDCConfigurationRepresentation discovery =
                KubernetesUtils.fetchJson(session, wellKnown, "application/json", OIDCConfigurationRepresentation.class, authMode);

        String jwksUri = discovery != null ? discovery.getJwksUri() : null;
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("OIDC discovery document missing jwks_uri: " + wellKnown);
        }

        JSONWebKeySet jwks =
                KubernetesUtils.fetchJson(session, jwksUri, "application/jwk-set+json", JSONWebKeySet.class, authMode);
        if (jwks == null || jwks.getKeys() == null) {
            throw new IllegalStateException("JWKS document had no keys: " + jwksUri);
        }

        return JWKSUtils.getKeyWrappersForUse(jwks, JWK.Use.SIG);
    }
}
