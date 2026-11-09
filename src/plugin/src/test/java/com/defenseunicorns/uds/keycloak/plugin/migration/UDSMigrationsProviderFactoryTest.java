/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.Constants;
import org.keycloak.models.RealmModel;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ProtocolMapperDelegationSyncer;
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
    void registersPostMigrationAndPropagatesFailures() {
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
    void synchronizesUpdatedProfilesFromTheEvent() {
        // given
        KeycloakSessionFactory factory = mock(KeycloakSessionFactory.class);
        KeycloakSession session = mock(KeycloakSession.class);
        RealmModel realm = mock(RealmModel.class);
        RealmModel.RealmAttributeUpdateEvent event = mock(RealmModel.RealmAttributeUpdateEvent.class);
        when(event.getAttributeName()).thenReturn(Constants.CLIENT_PROFILES);
        when(event.getRealm()).thenReturn(realm);
        when(event.getKeycloakSession()).thenReturn(session);
        when(realm.getName()).thenReturn("uds");
        when(event.getAttributeValue()).thenReturn("""
                {"profiles":[{"name":"UDS","executors":[{"executor":"uds-operator-permissions",
                "configuration":{"use-default-allowed-protocol-mappers":false,
                "allowed-protocol-mappers":["custom-mapper"]}}]}]}
                """);
        new UDSMigrationsProviderFactory().postInit(factory);
        ArgumentCaptor<ProviderEventListener> listener = ArgumentCaptor.forClass(ProviderEventListener.class);
        verify(factory).register(listener.capture());

        // when
        listener.getValue().onEvent(event);

        // then
        verify(realm).setAttribute(ProtocolMapperDelegationSyncer.ALLOWED_MAPPERS_ATTRIBUTE, "custom-mapper");
        verifyNoInteractions(session);
    }

    @Test
    void ignoresOtherRealmsAndAttributes() {
        // given
        KeycloakSessionFactory factory = mock(KeycloakSessionFactory.class);
        RealmModel realm = mock(RealmModel.class);
        RealmModel.RealmAttributeUpdateEvent event = mock(RealmModel.RealmAttributeUpdateEvent.class);
        new UDSMigrationsProviderFactory().postInit(factory);
        ArgumentCaptor<ProviderEventListener> listener = ArgumentCaptor.forClass(ProviderEventListener.class);
        verify(factory).register(listener.capture());

        // when
        when(event.getAttributeName()).thenReturn("unrelated");
        listener.getValue().onEvent(event);
        when(event.getAttributeName()).thenReturn(Constants.CLIENT_PROFILES);
        when(event.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("master");
        listener.getValue().onEvent(event);

        // then
        verify(event, never()).getAttributeValue();
        verify(realm, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void propagatesInvalidProfileFailures() {
        // given
        KeycloakSessionFactory factory = mock(KeycloakSessionFactory.class);
        RealmModel realm = mock(RealmModel.class);
        RealmModel.RealmAttributeUpdateEvent event = mock(RealmModel.RealmAttributeUpdateEvent.class);
        when(event.getAttributeName()).thenReturn(Constants.CLIENT_PROFILES);
        when(event.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("uds");
        when(event.getAttributeValue()).thenReturn("invalid json");
        new UDSMigrationsProviderFactory().postInit(factory);
        ArgumentCaptor<ProviderEventListener> listener = ArgumentCaptor.forClass(ProviderEventListener.class);
        verify(factory).register(listener.capture());

        // when / then
        assertThrows(IllegalStateException.class, () -> listener.getValue().onEvent(event));
        verify(realm, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void registersSpiAndProviderFactoryWithServiceLoader() {
        assertTrue(ServiceLoader.load(Spi.class).stream()
                .anyMatch(provider -> provider.type() == UDSMigrationsSpi.class));
        assertTrue(ServiceLoader.load(UDSMigrationsProviderFactory.class).iterator().hasNext());
    }
}
