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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UDSRealmFrontendUrlProviderFactoryTest {
    private static final String PUBLIC_FRONTEND_URL = "https://sso.uds.dev";
    private static final String ADMIN_FRONTEND_URL = "https://keycloak.admin.uds.dev";

    @Test
    void reconcilesUdsRealmWhenItIsCreated() {
        UDSRealmFrontendUrlProviderFactory factory = factory();
        KeycloakSession session = mock(KeycloakSession.class);
        RealmModel realm = realm("uds", "");
        RealmModel.RealmPostCreateEvent event = mock(RealmModel.RealmPostCreateEvent.class);
        when(event.getKeycloakSession()).thenReturn(session);
        when(event.getCreatedRealm()).thenReturn(realm);

        factory.onEvent(event);

        verify(realm).setAttribute("frontendUrl", PUBLIC_FRONTEND_URL);
    }

    @Test
    void reconcilesMasterRealmWhenItIsCreated() {
        UDSRealmFrontendUrlProviderFactory factory = factory();
        RealmModel realm = realm("master", "stale-value");
        RealmModel.RealmPostCreateEvent event = mock(RealmModel.RealmPostCreateEvent.class);
        when(event.getKeycloakSession()).thenReturn(mock(KeycloakSession.class));
        when(event.getCreatedRealm()).thenReturn(realm);

        factory.onEvent(event);

        verify(realm).setAttribute("frontendUrl", ADMIN_FRONTEND_URL);
    }

    @Test
    void doesNotOverwriteAnAlreadyCorrectFrontendUrl() {
        UDSRealmFrontendUrlProviderFactory factory = factory();
        RealmModel realm = realm("uds", PUBLIC_FRONTEND_URL);
        RealmModel.RealmPostCreateEvent event = mock(RealmModel.RealmPostCreateEvent.class);
        when(event.getKeycloakSession()).thenReturn(mock(KeycloakSession.class));
        when(event.getCreatedRealm()).thenReturn(realm);

        factory.onEvent(event);

        verify(realm, never()).setAttribute(eq("frontendUrl"), anyString());
    }

    @Test
    void ignoresOtherRealms() {
        UDSRealmFrontendUrlProviderFactory factory = factory();
        RealmModel realm = realm("other", "old-value");
        RealmModel.RealmPostCreateEvent event = mock(RealmModel.RealmPostCreateEvent.class);
        when(event.getKeycloakSession()).thenReturn(mock(KeycloakSession.class));
        when(event.getCreatedRealm()).thenReturn(realm);

        factory.onEvent(event);

        verify(realm, never()).setAttribute(eq("frontendUrl"), anyString());
    }

    @Test
    void reconcilesBothSupportedRealmsAfterMigration() {
        UDSRealmFrontendUrlProviderFactory factory = factory();
        KeycloakSessionFactory sessionFactory = mock(KeycloakSessionFactory.class);
        KeycloakSession session = mock(KeycloakSession.class);
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel master = realm("master", "old-admin-value");
        RealmModel uds = realm("uds", "old-public-value");
        when(session.realms()).thenReturn(realms);
        when(realms.getRealmByName("master")).thenReturn(master);
        when(realms.getRealmByName("uds")).thenReturn(uds);

        try (MockedStatic<KeycloakModelUtils> mocked = org.mockito.Mockito.mockStatic(KeycloakModelUtils.class)) {
            mocked.when(() -> KeycloakModelUtils.runJobInTransaction(eq(sessionFactory), any()))
                    .thenAnswer(invocation -> {
                        org.keycloak.models.KeycloakSessionTask task = invocation.getArgument(1);
                        task.run(session);
                        return null;
                    });

            factory.onEvent(new PostMigrationEvent(sessionFactory));
        }

        verify(master).setAttribute("frontendUrl", ADMIN_FRONTEND_URL);
        verify(uds).setAttribute("frontendUrl", PUBLIC_FRONTEND_URL);
    }

    @Test
    void ignoresMissingRealmsDuringMigration() {
        UDSRealmFrontendUrlProviderFactory factory = factory();
        KeycloakSessionFactory sessionFactory = mock(KeycloakSessionFactory.class);
        KeycloakSession session = mock(KeycloakSession.class);
        RealmProvider realms = mock(RealmProvider.class);
        when(session.realms()).thenReturn(realms);
        when(realms.getRealmByName("master")).thenReturn(null);
        when(realms.getRealmByName("uds")).thenReturn(null);

        try (MockedStatic<KeycloakModelUtils> mocked = org.mockito.Mockito.mockStatic(KeycloakModelUtils.class)) {
            mocked.when(() -> KeycloakModelUtils.runJobInTransaction(eq(sessionFactory), any()))
                    .thenAnswer(invocation -> {
                        org.keycloak.models.KeycloakSessionTask task = invocation.getArgument(1);
                        task.run(session);
                        return null;
                    });

            assertDoesNotThrow(() -> factory.onEvent(new PostMigrationEvent(sessionFactory)));
        }
    }

    private UDSRealmFrontendUrlProviderFactory factory() {
        return new UDSRealmFrontendUrlProviderFactory("uds.dev", "admin.uds.dev");
    }

    private RealmModel realm(String name, String frontendUrl) {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn(name);
        when(realm.getAttribute("frontendUrl")).thenReturn(frontendUrl);
        return realm;
    }
}
