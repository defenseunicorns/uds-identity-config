/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */
package com.defenseunicorns.uds.keycloak.plugin.migration;

import com.defenseunicorns.uds.keycloak.plugin.CustomAWSSAMLAttributeMapper;
import com.defenseunicorns.uds.keycloak.plugin.CustomGroupPathMapper;
import org.keycloak.protocol.oidc.mappers.AdminApiAudienceMapper;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ProtocolMapperDelegationSyncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.*;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.util.JsonSerialization;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrateTo26_8_2Test {
    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    @Mock private ClientScopeProvider scopes;
    @Mock private ClientScopeModel scope;
    @Mock private ClientModel operator;
    @Mock private KeycloakSessionFactory factory;
    @Captor private ArgumentCaptor<ProtocolMapperModel> mapperCaptor;

    @Test
    void createsScopeAndNativeAudienceMapper() {
        // given
        when(realm.getClientScopesStream()).thenReturn(Stream.empty());
        when(session.clientScopes()).thenReturn(scopes);
        when(scopes.addClientScope(realm, MigrateTo26_8_2.SCOPE_NAME)).thenReturn(scope);
        when(scope.getProtocolMappersStream()).thenReturn(Stream.empty());

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verify(scope).setProtocol("openid-connect");
        verify(scope).setAttribute("include.in.token.scope", "true");
        verify(scope).addProtocolMapper(mapperCaptor.capture());
        var mapper = mapperCaptor.getValue();
        assertEquals(AdminApiAudienceMapper.PROVIDER_ID, mapper.getProtocolMapper());
        assertNull(mapper.getConfig().get("included.custom.audience"));
        assertEquals("true", mapper.getConfig().get("access.token.claim"));
        assertEquals("true", mapper.getConfig().get("introspection.token.claim"));
        assertEquals("true", mapper.getConfig().get("lightweight.claim"));
    }

    @Test
    void assignsScopeToExistingUdsAdminClients() {
        // given
        existingScope();
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
        existingScope();
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
        existingScope();
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
    void reusesScopeAndPreservesNativeMapper() {
        // given
        var nativeMapper = new ProtocolMapperModel();
        nativeMapper.setName("renamed-native-mapper");
        nativeMapper.setProtocolMapper("oidc-admin-api-audience-mapper");
        existingScope(nativeMapper);

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verifyNoInteractions(scopes);
        verify(scope, never()).addProtocolMapper(any());
        verify(scope, never()).removeProtocolMapper(any());
        verify(scope, never()).updateProtocolMapper(nativeMapper);
    }

    @Test
    void addsNativeMapperWhenExistingMapperHasAnotherProvider() {
        // given
        var mapper = new ProtocolMapperModel();
        mapper.setName("existing-audience");
        mapper.setProtocolMapper("oidc-audience-mapper");
        existingScope(mapper);

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verify(scope).addProtocolMapper(mapperCaptor.capture());
        assertEquals(AdminApiAudienceMapper.PROVIDER_ID, mapperCaptor.getValue().getProtocolMapper());
        verify(scope, never()).updateProtocolMapper(any());
        assertEquals("oidc-audience-mapper", mapper.getProtocolMapper());
    }

    @Test
    void doesNotDuplicateExistingClientScopeAssignment() {
        // given
        existingScope();
        when(realm.getClientByClientId("uds-operator")).thenReturn(operator);
        when(operator.getClientScopes(true)).thenReturn(Map.of(MigrateTo26_8_2.SCOPE_NAME, scope));

        // when
        new MigrateTo26_8_2().migrate(session, realm);

        // then
        verify(operator, never()).addClientScope(any(), anyBoolean());
    }

    private void existingScope(ProtocolMapperModel... mappers) {
        when(realm.getClientScopesStream()).thenReturn(Stream.of(scope));
        when(scope.getName()).thenReturn(MigrateTo26_8_2.SCOPE_NAME);
        when(scope.getProtocolMappersStream()).thenReturn(Stream.of(mappers));
    }

    private static String profiles(Map<String, Object> configuration) throws Exception {
        return JsonSerialization.writeValueAsString(Map.of("profiles", List.of(Map.of(
                "name", "UDS", "executors", List.of(Map.of(
                        "executor", "uds-operator-permissions", "configuration", configuration))))));
    }
}
