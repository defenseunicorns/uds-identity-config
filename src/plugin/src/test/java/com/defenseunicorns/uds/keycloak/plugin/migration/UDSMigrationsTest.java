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
import org.keycloak.models.KeycloakTransactionManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class UDSMigrationsTest {
    @Test
    void failedMigrationDoesNotPersistCompletionMarkerAndReleasesLock() {
        // given
        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakSessionFactory factory = mock(KeycloakSessionFactory.class);
        KeycloakSession migrationSession = mock(KeycloakSession.class);
        when(session.getKeycloakSessionFactory()).thenReturn(factory);
        when(factory.create()).thenReturn(migrationSession);
        KeycloakTransactionManager transaction = mock(KeycloakTransactionManager.class);
        when(migrationSession.getTransactionManager()).thenReturn(transaction);
        when(session.getProvider(DBLockProvider.class)).thenReturn(mock(DBLockProvider.class));
        DBLockProvider lock = session.getProvider(DBLockProvider.class);
        when(migrationSession.getContext()).thenReturn(mock(KeycloakContext.class));
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel realm = mock(RealmModel.class);
        when(migrationSession.realms()).thenReturn(realms);
        when(realms.getRealmByName("uds")).thenReturn(realm);
        IllegalStateException failure = new IllegalStateException("migration failed");
        when(realm.getClientScopesStream()).thenThrow(failure);

        // when / then
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new UDSMigrations(session).run()));
        verify(transaction).begin();
        verify(transaction).setRollbackOnly();
        verify(migrationSession).close();

        verify(realm, never()).setAttribute("uds.keycloak.migration.26.8.2", true);
        verify(lock).waitForLock(DBLockProvider.Namespace.KEYCLOAK_BOOT);
        verify(lock).releaseLock();
    }
}
