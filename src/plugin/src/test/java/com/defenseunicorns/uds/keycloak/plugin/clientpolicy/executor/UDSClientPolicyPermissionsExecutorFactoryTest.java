/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;

import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

class UDSClientPolicyPermissionsExecutorFactoryTest {

    private static final String MIGRATION_COMPLETE = "uds.keycloak.26.8.2.migrationComplete";

    @Test
    void migratesExistingRealmOnlyOnce() {
        KeycloakSession session = mock(KeycloakSession.class);
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel realm = mock(RealmModel.class);
        ClientScopeModel scope = mock(ClientScopeModel.class);
        ClientModel operator = mock(ClientModel.class);
        ClientModel fleet = mock(ClientModel.class);
        ClientModel opentofu = mock(ClientModel.class);
        when(session.realms()).thenReturn(realms);
        when(realms.getRealmByName("uds")).thenReturn(realm);
        when(realm.getAttribute(MIGRATION_COMPLETE)).thenReturn(null, "true");
        when(realm.getClientScopesStream()).thenReturn(Stream.of(scope));
        when(scope.getName()).thenReturn("kc-admin-api");
        when(realm.getClientByClientId("uds-operator")).thenReturn(operator);
        when(realm.getClientByClientId("uds-fleet-admin")).thenReturn(fleet);
        when(realm.getClientByClientId("uds-opentofu-client")).thenReturn(opentofu);
        when(operator.getClientScopes(true)).thenReturn(Map.of());
        when(fleet.getClientScopes(true)).thenReturn(Map.of());
        when(opentofu.getClientScopes(true)).thenReturn(Map.of());

        UDSClientPolicyPermissionsExecutorFactory factory = new UDSClientPolicyPermissionsExecutorFactory();
        factory.migrateAdminApiConfiguration(session);
        factory.migrateAdminApiConfiguration(session);

        verify(operator, times(1)).addClientScope(scope, true);
        verify(fleet, times(1)).addClientScope(scope, true);
        verify(opentofu, times(1)).addClientScope(scope, true);
        verify(realm, times(1)).setAttribute("allowedProtocolMapperTypesForClientAdministrators",
                String.join(",", UDSClientPolicyPermissionsExecutor.DEFAULT_ALLOWED_PROTOCOL_MAPPERS));
        verify(realm, times(1)).setAttribute(MIGRATION_COMPLETE, "true");
        verify(realm, times(1)).getClientScopesStream();
    }

    @Test
    void waitsForAdminScopeBeforeMigrating() {
        KeycloakSession session = mock(KeycloakSession.class);
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel realm = mock(RealmModel.class);
        when(session.realms()).thenReturn(realms);
        when(realms.getRealmByName("uds")).thenReturn(realm);
        when(realm.getClientScopesStream()).thenReturn(Stream.empty());

        new UDSClientPolicyPermissionsExecutorFactory().migrateAdminApiConfiguration(session);

        verify(realm, never()).setAttribute(eq(MIGRATION_COMPLETE), anyString());
    }
}
