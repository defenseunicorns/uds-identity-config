/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */
package com.defenseunicorns.uds.keycloak.plugin.migration;

import com.defenseunicorns.uds.keycloak.plugin.CustomAWSSAMLAttributeMapper;
import com.defenseunicorns.uds.keycloak.plugin.CustomGroupPathMapper;
import org.keycloak.migration.MigrationProvider;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ProtocolMapperDelegationSyncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.*;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.util.JsonSerialization;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrateTo26_8_2Test {
    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    @Mock private MigrationProvider migration;
    @Mock private ClientScopeModel scope;
    @Mock private ClientModel operator;
    @Mock private KeycloakSessionFactory factory;

    @Test
    void delegatesScopeCreationToKeycloak() {
        // given
        adminApiScope();

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verify(migration).addOIDCAdminApiClientScope(realm);
        verifyNoInteractions(scope);
    }

    @Test
    void assignsScopeToExistingUdsAdminClients() {
        // given
        adminApiScope();
        var clients = List.of(operator, mock(ClientModel.class), mock(ClientModel.class));
        var clientIds = List.of("uds-operator", "uds-fleet-admin", "uds-opentofu-client");
        for (int i = 0; i < clients.size(); i++) {
            when(realm.getClientByClientId(clientIds.get(i))).thenReturn(clients.get(i));
            when(clients.get(i).getClientScopes(true)).thenReturn(Map.of());
        }

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        clients.forEach(client -> verify(client).addClientScope(scope, true));
        verify(realm, never()).getClientByClientId("admin-cli");
        verify(realm, never()).getClientByClientId("security-admin-console");
    }

    @Test
    void mergesDefaultsConfiguredTypesAndExistingAllowances() throws Exception {
        // given
        adminApiScope();
        when(realm.getAttribute(Constants.CLIENT_PROFILES)).thenReturn(profiles(Map.of(
                "allowed-protocol-mappers-as-string", "custom-from-profile, custom-extra",
                "allowed-protocol-mappers", List.of("custom-list"))));
        when(realm.getAttribute(ProtocolMapperDelegationSyncer.ALLOWED_MAPPERS_ATTRIBUTE)).thenReturn("custom-existing");
        when(session.getKeycloakSessionFactory()).thenReturn(factory);
        when(factory.getProviderFactoriesStream(ProtocolMapper.class)).thenReturn(Stream.of(
                new CustomGroupPathMapper(), new CustomAWSSAMLAttributeMapper()));

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verify(realm).setAttribute(eq(ProtocolMapperDelegationSyncer.ALLOWED_MAPPERS_ATTRIBUTE), argThat((String value) ->
                List.of(value.split(",")).containsAll(List.of("custom-existing", "oidc-audience-mapper",
                        "bare-group-path-mapper", "aws-saml-attribute-mapper",
                        "custom-from-profile", "custom-extra", "custom-list"))));
    }

    @Test
    void honorsDisabledDefaults() throws Exception {
        // given
        adminApiScope();
        when(realm.getAttribute(Constants.CLIENT_PROFILES)).thenReturn(profiles(Map.of(
                "use-default-allowed-protocol-mappers", false,
                "allowed-protocol-mappers-as-string", "from-string, shared",
                "allowed-protocol-mappers", List.of("from-list", "shared"))));
        when(realm.getAttribute(ProtocolMapperDelegationSyncer.ALLOWED_MAPPERS_ATTRIBUTE)).thenReturn("custom-existing");

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verify(realm).setAttribute(ProtocolMapperDelegationSyncer.ALLOWED_MAPPERS_ATTRIBUTE,
                "custom-existing,from-list,from-string,shared");
    }

    @Test
    void doesNotDuplicateExistingClientScopeAssignment() {
        // given
        adminApiScope();
        when(realm.getClientByClientId("uds-operator")).thenReturn(operator);
        when(operator.getClientScopes(true)).thenReturn(Map.of(MigrateTo26_8_2.SCOPE_NAME, scope));

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verify(operator, never()).addClientScope(any(), anyBoolean());
    }

    private void adminApiScope() {
        when(session.getProvider(MigrationProvider.class)).thenReturn(migration);
        when(migration.addOIDCAdminApiClientScope(realm)).thenReturn(scope);
    }

    private static String profiles(Map<String, Object> configuration) throws Exception {
        return JsonSerialization.writeValueAsString(Map.of("profiles", List.of(Map.of(
                "name", "UDS", "executors", List.of(Map.of(
                        "executor", "uds-operator-permissions", "configuration", configuration))))));
    }
}
