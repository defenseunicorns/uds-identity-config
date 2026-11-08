/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.dblock.DBLockProvider;
import org.keycloak.models.utils.KeycloakModelUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UDSMigrationsTest {
    @Test
    void failedMigrationDoesNotPersistCompletionMarkerAndReleasesLock() {
        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakSessionFactory factory = mock(KeycloakSessionFactory.class);
        KeycloakSession migrationSession = mock(KeycloakSession.class);
        when(session.getKeycloakSessionFactory()).thenReturn(factory);
        when(session.getProvider(DBLockProvider.class)).thenReturn(mock(DBLockProvider.class));
        DBLockProvider lock = session.getProvider(DBLockProvider.class);
        when(migrationSession.getContext()).thenReturn(mock(KeycloakContext.class));
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel realm = mock(RealmModel.class);
        when(migrationSession.realms()).thenReturn(realms);
        when(realms.getRealmByName("uds")).thenReturn(realm);
        when(realm.getClientScopesStream()).thenThrow(new IllegalStateException("migration failed"));

        try (var modelUtils = mockStatic(KeycloakModelUtils.class)) {
            modelUtils.when(() -> KeycloakModelUtils.runJobInTransaction(eq(factory), any()))
                    .thenAnswer(invocation -> {
                        invocation.<org.keycloak.models.KeycloakSessionTask>getArgument(1).run(migrationSession);
                        return null;
                    });

            assertThrows(IllegalStateException.class, () -> new UDSMigrations(session).run());
        }

        verify(realm, never()).setAttribute("uds.keycloak.migration.26.8.2", true);
        verify(lock).waitForLock(DBLockProvider.Namespace.KEYCLOAK_BOOT);
        verify(lock).releaseLock();
    }
}
