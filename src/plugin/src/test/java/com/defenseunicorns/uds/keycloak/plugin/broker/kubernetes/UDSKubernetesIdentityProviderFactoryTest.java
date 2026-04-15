/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory.ClientAssertionStrategy;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UDSKubernetesIdentityProviderFactoryTest {

    @Mock private KeycloakSession session;

    private final UDSKubernetesIdentityProviderFactory factory = new UDSKubernetesIdentityProviderFactory();

    @Test
    void testGetIdReturnsCorrectProviderId() {
        assertEquals("uds-kubernetes", factory.getId());
    }

    @Test
    void testCreateReturnsUDSKubernetesIdentityProvider() {
        IdentityProviderModel model = new IdentityProviderModel();
        model.getConfig().put("issuer", "https://kubernetes.default.svc.cluster.local");

        UDSKubernetesIdentityProvider provider = factory.create(session, model);

        assertNotNull(provider);
        assertInstanceOf(UDSKubernetesIdentityProvider.class, provider);
    }

    @Test
    void testGetClientAssertionStrategyReturnsNonNull() {
        ClientAssertionStrategy strategy = factory.getClientAssertionStrategy();
        assertNotNull(strategy);
    }
}
