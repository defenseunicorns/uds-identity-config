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
 * forwarding. Both the discovery and JWKS fetches go through {@link KubernetesUtils#fetchJson}, which attaches the
 * pod service-account token only when the destination is the in-cluster Kubernetes API server. So a public JWKS
 * (S3/EKS/AKS OIDC) is fetched anonymously, while the in-cluster apiserver JWKS gets the token.
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> delete once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> lets the stock
 * provider control whether the service-account token is forwarded.
 */
public class UDSKubernetesJwksEndpointLoader implements PublicKeyLoader {

    private final KeycloakSession session;
    private final String discoveryBaseUrl;

    public UDSKubernetesJwksEndpointLoader(KeycloakSession session, String discoveryBaseUrl) {
        this.session = session;
        this.discoveryBaseUrl = discoveryBaseUrl;
    }

    @Override
    public PublicKeysWrapper loadKeys() throws Exception {
        String wellKnown = KubernetesUtils.wellKnownUrl(discoveryBaseUrl);
        OIDCConfigurationRepresentation discovery = KubernetesUtils.fetchJson(session, wellKnown, "application/json",
                OIDCConfigurationRepresentation.class, KubernetesUtils.isTrustedKubernetesApiUrl(discoveryBaseUrl));

        String jwksUri = discovery != null ? discovery.getJwksUri() : null;
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("OIDC discovery document missing jwks_uri: " + wellKnown);
        }

        JSONWebKeySet jwks = KubernetesUtils.fetchJson(session, jwksUri, "application/jwk-set+json",
                JSONWebKeySet.class, KubernetesUtils.isTrustedKubernetesApiJwksUrl(jwksUri, discoveryBaseUrl));
        if (jwks == null || jwks.getKeys() == null) {
            throw new IllegalStateException("JWKS document had no keys: " + jwksUri);
        }

        return JWKSUtils.getKeyWrappersForUse(jwks, JWK.Use.SIG);
    }
}
