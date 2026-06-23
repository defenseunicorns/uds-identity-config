/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.junit.jupiter.api.Test;
import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests the fail-loud behavior of {@link UDSKubernetesJwksEndpointLoader#loadKeys}: a discovery or JWKS fetch that
 * yields no usable keys must throw rather than return an empty key set (which would make signature verification
 * silently fail or behave unpredictably). The underlying HTTP/token-forwarding is covered by
 * {@link KubernetesUtilsTest}, so it is stubbed here.
 */
class UDSKubernetesJwksEndpointLoaderTest {

    private static final String BASE_URL = "https://issuer.example";
    private static final String JWKS_URI = "https://issuer.example/openid/v1/jwks";
    private static final UDSKubernetesHttpAuthPolicy.Mode MODE = UDSKubernetesHttpAuthPolicy.Mode.AUTO;

    private final KeycloakSession session = mock(KeycloakSession.class);
    private final UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, BASE_URL, MODE);

    private void stubDiscovery(MockedStatic<KubernetesUtils> utils, OIDCConfigurationRepresentation discovery) {
        utils.when(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/json"),
                eq(OIDCConfigurationRepresentation.class), any())).thenReturn(discovery);
    }

    private void stubJwks(MockedStatic<KubernetesUtils> utils, JSONWebKeySet jwks) {
        utils.when(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/jwk-set+json"),
                eq(JSONWebKeySet.class), any())).thenReturn(jwks);
    }

    @Test
    void throwsWhenDiscoveryDocumentIsNull() {
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, null);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void throwsWhenJwksUriMissing() {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn(null);
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void throwsWhenJwksUriBlank() {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn("   ");
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void throwsWhenJwksDocumentHasNoKeys() {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn(JWKS_URI);
        JSONWebKeySet jwks = new JSONWebKeySet(); // default getKeys() == null
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            stubJwks(utils, jwks);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void returnsWrapperWhenKeysPresent() throws Exception {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn(JWKS_URI);
        JSONWebKeySet jwks = new JSONWebKeySet();
        jwks.setKeys(new JWK[0]); // non-null key array clears the guard
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            stubJwks(utils, jwks);
            PublicKeysWrapper wrapper = loader.loadKeys();
            assertNotNull(wrapper);
        }
    }
}
