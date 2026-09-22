/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.email;

import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UDSEmailTemplateProviderFactoryTest {
    private static final String ADMIN_LINK =
            "https://keycloak.admin.uds.dev/realms/uds/login-actions/action-token?key=not-a-token";

    @Test
    void acceptsHostOnlyHostnameAndLeavesLinksUnchanged() throws Exception {
        UDSEmailTemplateProviderFactory factory = factoryWithHostname("sso.example");
        UDSEmailTemplateProvider provider = providerFrom(factory);

        assertEquals(ADMIN_LINK, provider.publicExecuteActionsLink(ADMIN_LINK));
    }

    @Test
    void acceptsFullHostnameUrl() {
        assertDoesNotThrow(() -> factoryWithHostname("https://sso.example"));
    }

    @Test
    void rejectsUnsupportedHostnameValues() {
        Config.Scope config = mock(Config.Scope.class);
        when(config.get("hostname")).thenReturn("ftp://sso.example");

        assertThrows(IllegalArgumentException.class, () -> new UDSEmailTemplateProviderFactory().init(config));
    }

    private static UDSEmailTemplateProviderFactory factoryWithHostname(String hostname) {
        Config.Scope config = mock(Config.Scope.class);
        when(config.get("hostname")).thenReturn(hostname);

        UDSEmailTemplateProviderFactory factory = new UDSEmailTemplateProviderFactory();
        factory.init(config);
        return factory;
    }

    private static UDSEmailTemplateProvider providerFrom(UDSEmailTemplateProviderFactory factory) {
        EmailTemplateProvider provider = factory.create(mock(KeycloakSession.class));
        return (UDSEmailTemplateProvider) provider;
    }
}
