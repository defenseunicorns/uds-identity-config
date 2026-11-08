/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;
import org.keycloak.provider.Spi;
import org.mockito.ArgumentCaptor;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class UDSMigrationsProviderFactoryTest {
    @Test
    void registersOnlyPostMigrationAndPropagatesFailures() {
        KeycloakSessionFactory factory = mock(KeycloakSessionFactory.class);
        KeycloakSession session = mock(KeycloakSession.class);
        UDSMigrations migrations = mock(UDSMigrations.class);
        RuntimeException failure = new IllegalStateException("migration failed");
        when(factory.create()).thenReturn(session);
        when(session.getProvider(UDSMigrations.class)).thenReturn(migrations);
        doThrow(failure).when(migrations).run();

        new UDSMigrationsProviderFactory().postInit(factory);
        ArgumentCaptor<ProviderEventListener> listener = ArgumentCaptor.forClass(ProviderEventListener.class);
        verify(factory).register(listener.capture());

        listener.getValue().onEvent(mock(ProviderEvent.class));
        verify(factory, never()).create();
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> listener.getValue().onEvent(new PostMigrationEvent(factory))));
        verify(session).close();
    }

    @Test
    void registersSpiAndProviderFactoryWithServiceLoader() {
        assertTrue(ServiceLoader.load(Spi.class).stream()
                .anyMatch(provider -> provider.type() == UDSMigrationsSpi.class));
        assertTrue(ServiceLoader.load(UDSMigrationsProviderFactory.class).iterator().hasNext());
    }
}
