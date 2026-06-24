/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.utils.KeycloakSessionUtil;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Tests the resolve-and-persist step that runs at IdP validation: discover the issuer once and store it so the
 * stock client-assertion lookup works, fail (don't persist) if discovery fails, and let an explicitly configured
 * issuer win.
 */
class UDSKubernetesIdentityProviderConfigTest {

    private static final String DISCOVERY_URL = "https://kubernetes.default.svc.cluster.local";
    private static final String RESOLVED_ISSUER = "https://oidc.cluster.example/id/abc";

    private UDSKubernetesIdentityProviderConfig config(String autoDiscovery, String issuer, String discoveryUrl) {
        UDSKubernetesIdentityProviderConfig c = new UDSKubernetesIdentityProviderConfig();
        Map<String, String> m = new HashMap<>();
        if (autoDiscovery != null) {
            m.put(UDSKubernetesIdentityProviderConfig.AUTOMATIC_ISSUER_DISCOVERY, autoDiscovery);
        }
        if (issuer != null) {
            m.put(UDSKubernetesIdentityProviderConfig.ISSUER, issuer);
        }
        if (discoveryUrl != null) {
            m.put(UDSKubernetesIdentityProviderConfig.ISSUER_DISCOVERY_URL, discoveryUrl);
        }
        c.setConfig(m);
        return c;
    }

    @Test
    void resolvesAndPersistsWhenDiscoveryOnAndNoIssuer() {
        UDSKubernetesIdentityProviderConfig c = config("true", null, DISCOVERY_URL);
        try (MockedStatic<KeycloakSessionUtil> ksu = mockStatic(KeycloakSessionUtil.class);
             MockedStatic<KubernetesUtils> ku = mockStatic(KubernetesUtils.class)) {
            ksu.when(KeycloakSessionUtil::getKeycloakSession).thenReturn(mock(KeycloakSession.class));
            ku.when(() -> KubernetesUtils.resolveIssuer(any(), eq(DISCOVERY_URL))).thenReturn(RESOLVED_ISSUER);

            c.resolveAndPersistIssuer();

            assertEquals(RESOLVED_ISSUER, c.getIssuer());
        }
    }

    @Test
    void propagatesWhenDiscoveryFails() {
        UDSKubernetesIdentityProviderConfig c = config("true", null, DISCOVERY_URL);
        try (MockedStatic<KeycloakSessionUtil> ksu = mockStatic(KeycloakSessionUtil.class);
             MockedStatic<KubernetesUtils> ku = mockStatic(KubernetesUtils.class)) {
            ksu.when(KeycloakSessionUtil::getKeycloakSession).thenReturn(mock(KeycloakSession.class));
            ku.when(() -> KubernetesUtils.resolveIssuer(any(), anyString()))
                    .thenThrow(new IllegalArgumentException("discovery unreachable"));

            assertThrows(IllegalArgumentException.class, c::resolveAndPersistIssuer);
        }
    }

    @Test
    void skipsDiscoveryWhenIssuerExplicitlySet() {
        UDSKubernetesIdentityProviderConfig c = config("true", "https://pinned.issuer.example", null);
        try (MockedStatic<KubernetesUtils> ku = mockStatic(KubernetesUtils.class)) {
            c.resolveAndPersistIssuer();

            assertEquals("https://pinned.issuer.example", c.getIssuer());
            ku.verify(() -> KubernetesUtils.resolveIssuer(any(), anyString()), never());
        }
    }

    @Test
    void skipsDiscoveryWhenAutoDiscoveryOff() {
        UDSKubernetesIdentityProviderConfig c = config("false", null, DISCOVERY_URL);
        try (MockedStatic<KubernetesUtils> ku = mockStatic(KubernetesUtils.class)) {
            c.resolveAndPersistIssuer();

            ku.verify(() -> KubernetesUtils.resolveIssuer(any(), anyString()), never());
        }
    }
}
