/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UDSFrontendUrlMigrationProviderFactoryTest {
    private static final String GENERATED_URL = "https://sso.uds.dev";

    @Test
    void registersForProviderLifecycleEvents() {
        UDSFrontendUrlMigrationProviderFactory factory = new UDSFrontendUrlMigrationProviderFactory("uds.dev");
        KeycloakSessionFactory sessionFactory = mock(KeycloakSessionFactory.class);

        factory.postInit(sessionFactory);

        verify(sessionFactory).register(factory);
    }

    @Test
    void dispatchesPostMigrationEventAndRunsMigrationInTransaction() {
        UDSFrontendUrlMigrationProviderFactory factory = new UDSFrontendUrlMigrationProviderFactory("uds.dev");
        KeycloakSessionFactory sessionFactory = mock(KeycloakSessionFactory.class);
        KeycloakSession session = mock(KeycloakSession.class);
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel realm = mock(RealmModel.class);
        when(session.realms()).thenReturn(realms);
        when(realms.getRealmByName("uds")).thenReturn(realm);
        when(realm.getName()).thenReturn("uds");
        when(realm.getAttribute(UDSFrontendUrlMigration.COMPLETION_ATTRIBUTE)).thenReturn(null);
        when(realm.getAttribute(UDSFrontendUrlMigration.FRONTEND_URL_ATTRIBUTE)).thenReturn(GENERATED_URL);

        try (MockedStatic<KeycloakModelUtils> mocked = mockStatic(KeycloakModelUtils.class)) {
            mocked.when(() -> KeycloakModelUtils.runJobInTransaction(eq(sessionFactory), any()))
                    .thenAnswer(invocation -> {
                        org.keycloak.models.KeycloakSessionTask task = invocation.getArgument(1);
                        task.run(session);
                        return null;
                    });

            factory.onEvent(new PostMigrationEvent(sessionFactory));

            mocked.verify(() -> KeycloakModelUtils.runJobInTransaction(eq(sessionFactory), any()));
        }

        verify(realm).removeAttribute(UDSFrontendUrlMigration.FRONTEND_URL_ATTRIBUTE);
        verify(realm).setAttribute(UDSFrontendUrlMigration.COMPLETION_ATTRIBUTE, "complete");
    }

    @Test
    void serviceDescriptorRegistersMigrationFactory() throws Exception {
        String serviceName = "META-INF/services/org.keycloak.events.EventListenerProviderFactory";
        try (var service = getClass().getClassLoader().getResourceAsStream(serviceName)) {
            assertNotNull(service, "Event listener provider service descriptor must be packaged");
            String providers = new String(service.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(
                    providers.lines().anyMatch(line -> line.equals(UDSFrontendUrlMigrationProviderFactory.class.getName())),
                    "Migration factory must be registered as an event listener provider factory");
        }
    }
}
