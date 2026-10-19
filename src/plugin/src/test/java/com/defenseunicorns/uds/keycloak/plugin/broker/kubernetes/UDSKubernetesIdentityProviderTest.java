/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.junit.jupiter.api.Test;
import org.keycloak.broker.kubernetes.KubernetesIdentityProvider;
import org.keycloak.broker.kubernetes.KubernetesIdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class UDSKubernetesIdentityProviderTest {

    @Test
    void passesRealmAttributesToUpstreamProvider() {
        Map<String, String> attributes = Map.of(
                "issuerDiscoveryUrl", "https://kubernetes.default.svc",
                "automaticIssuerDiscovery", "true",
                "issuer", "");
        IdentityProviderModel model = new IdentityProviderModel();
        model.setConfig(attributes);

        KubernetesIdentityProvider provider = new UDSKubernetesIdentityProviderFactory()
                .create(mock(KeycloakSession.class), model);
        KubernetesIdentityProviderConfig config = provider.getConfig();

        assertEquals(attributes, config.getConfig());
        assertEquals(attributes.get("issuerDiscoveryUrl"), config.getIssuerDiscoveryUrl());
    }
}
