/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import com.defenseunicorns.uds.keycloak.plugin.CustomAWSSAMLAttributeMapper;
import com.defenseunicorns.uds.keycloak.plugin.CustomGroupPathMapper;
import com.defenseunicorns.uds.keycloak.plugin.UDSAdminApiAudienceMapper;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.models.RealmModel;
import org.keycloak.models.ClientScopeProvider;
import org.keycloak.json.RawJsonValue;
import org.keycloak.representations.idm.ClientPolicyExecutorRepresentation;
import org.keycloak.representations.idm.ClientProfileRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyManager;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Stream;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MigrateTo26_8_2Test {
    private final KeycloakSession session = mock(KeycloakSession.class);
    private final KeycloakContext context = mock(KeycloakContext.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final ClientScopeProvider scopes = mock(ClientScopeProvider.class);
    private final ClientPolicyManager policies = mock(ClientPolicyManager.class);
    private final KeycloakSessionFactory factory = mock(KeycloakSessionFactory.class);
    private void setupSession() throws Exception {
        when(session.getContext()).thenReturn(context);
        when(session.clientScopes()).thenReturn(scopes);
        when(session.clientPolicy()).thenReturn(policies);
        when(session.getKeycloakSessionFactory()).thenReturn(factory);
        when(factory.getProviderFactoriesStream(ProtocolMapper.class)).thenAnswer(invocation -> Stream.of(
                new CustomGroupPathMapper(), new CustomAWSSAMLAttributeMapper(), new UDSAdminApiAudienceMapper()));
        when(policies.getClientProfiles(realm, false)).thenReturn(null);
        when(realm.getName()).thenReturn("uds");
        when(realm.getAttribute(MigrateTo26_8_2.ALLOWED_MAPPERS_ATTRIBUTE)).thenReturn("custom-existing");
    }

    @Test
    void createsCompatibleScopeAndAssignsExistingAdminClient() throws Exception {
        setupSession();
        ClientPolicyExecutorRepresentation executor = new ClientPolicyExecutorRepresentation();
        executor.setExecutorProviderId("uds-operator-permissions");
        executor.setConfiguration(RawJsonValue.of(Map.of(
                "allowed-protocol-mappers-as-string", "custom-from-profile, custom-extra",
                "allowed-protocol-mappers", List.of("custom-list"))));
        ClientProfileRepresentation profile = new ClientProfileRepresentation();
        profile.setExecutors(List.of(executor));
        ClientProfilesRepresentation profiles = new ClientProfilesRepresentation();
        profiles.setProfiles(List.of(profile));
        when(policies.getClientProfiles(realm, false)).thenReturn(profiles);
        ClientScopeModel scope = mock(ClientScopeModel.class);
        ClientModel operator = mock(ClientModel.class);
        when(realm.getClientScopesStream()).thenReturn(Stream.empty());
        when(scopes.addClientScope(realm, MigrateTo26_8_2.SCOPE_NAME)).thenReturn(scope);
        when(scope.getProtocolMappersStream()).thenReturn(Stream.empty());
        when(operator.getClientScopes(true)).thenReturn(Map.of());
        when(realm.getClientByClientId(anyString())).thenAnswer(invocation ->
                "uds-operator".equals(invocation.getArgument(0)) ? operator : null);

        new MigrateTo26_8_2().migrate(session, realm);

        ArgumentCaptor<ProtocolMapperModel> mapper = ArgumentCaptor.forClass(ProtocolMapperModel.class);
        verify(scope).addProtocolMapper(mapper.capture());
        assertEquals("uds-admin-api-audience-mapper", mapper.getValue().getProtocolMapper());
        assertNull(mapper.getValue().getConfig().get("included.custom.audience"));
        verify(scope).setAttribute("include.in.token.scope", "true");
        assertEquals("true", mapper.getValue().getConfig().get("introspection.token.claim"));
        assertEquals("true", mapper.getValue().getConfig().get("lightweight.claim"));
        verify(context, never()).getUri(any());
        verify(operator).addClientScope(scope, true);
        verify(realm).setAttribute(eq(MigrateTo26_8_2.ALLOWED_MAPPERS_ATTRIBUTE), argThat((String value) ->
                value.contains("custom-existing") && value.contains("oidc-audience-mapper")
                        && value.contains("bare-group-path-mapper") && value.contains("aws-saml-attribute-mapper")
                        && value.contains("uds-admin-api-audience-mapper")
                        && value.contains("custom-from-profile") && value.contains("custom-extra")
                        && value.contains("custom-list")));
    }

    @Test
    void honorsDisabledDefaultsAndConfiguredMapperTypes() throws Exception {
        setupSession();
        ClientPolicyExecutorRepresentation executor = new ClientPolicyExecutorRepresentation();
        executor.setExecutorProviderId("uds-operator-permissions");
        executor.setConfiguration(RawJsonValue.of(Map.of(
                "use-default-allowed-protocol-mappers", "false",
                "allowed-protocol-mappers-as-string", "from-string, shared",
                "allowed-protocol-mappers", List.of("from-list", "shared"))));
        ClientProfileRepresentation profile = new ClientProfileRepresentation();
        profile.setExecutors(List.of(executor));
        ClientProfilesRepresentation profiles = new ClientProfilesRepresentation();
        profiles.setProfiles(List.of(profile));
        when(policies.getClientProfiles(realm, false)).thenReturn(profiles);
        ClientScopeModel scope = mock(ClientScopeModel.class);
        when(realm.getClientScopesStream()).thenReturn(Stream.of(scope));
        when(scope.getName()).thenReturn(MigrateTo26_8_2.SCOPE_NAME);
        when(scope.getProtocolMappersStream()).thenReturn(Stream.empty());

        new MigrateTo26_8_2().migrate(session, realm);

        verify(realm).setAttribute(eq(MigrateTo26_8_2.ALLOWED_MAPPERS_ATTRIBUTE), argThat((String value) ->
                value.contains("custom-existing") && value.contains("from-string") && value.contains("from-list")
                        && !value.contains("bare-group-path-mapper") && !value.contains("uds-admin-api-audience-mapper")));
    }

    @Test
    void reusesScopeAndKeepsExistingMapperAndClientAssignment() throws Exception {
        setupSession();
        ClientScopeModel scope = mock(ClientScopeModel.class);
        ProtocolMapperModel nativeMapper = mock(ProtocolMapperModel.class);
        ClientModel operator = mock(ClientModel.class);
        when(realm.getClientScopesStream()).thenReturn(Stream.of(scope));
        when(scope.getName()).thenReturn(MigrateTo26_8_2.SCOPE_NAME);
        when(scope.getProtocolMappersStream()).thenReturn(Stream.of(nativeMapper));
        when(nativeMapper.getName()).thenReturn("admin-api-audience");
        when(nativeMapper.getProtocolMapper()).thenReturn("oidc-admin-api-audience-mapper");
        when(realm.getClientByClientId("uds-operator")).thenReturn(operator);
        when(realm.getClientByClientId(anyString())).thenAnswer(invocation ->
                "uds-operator".equals(invocation.getArgument(0)) ? operator : null);
        when(operator.getClientScopes(true)).thenReturn(Map.of(MigrateTo26_8_2.SCOPE_NAME, scope));

        new MigrateTo26_8_2().migrate(session, realm);

        verify(scopes, never()).addClientScope(any(), anyString());
        verify(scope).addProtocolMapper(any());
        verify(scope, never()).removeProtocolMapper(any());
        verify(scope, never()).updateProtocolMapper(nativeMapper);
        verify(operator, never()).addClientScope(any(), anyBoolean());
    }

    @Test
    void updatesCompatibleMapperAndDoesNotDuplicateScopeAssignment() throws Exception {
        setupSession();
        ClientScopeModel scope = mock(ClientScopeModel.class);
        ProtocolMapperModel compatibleMapper = mock(ProtocolMapperModel.class);
        ClientModel operator = mock(ClientModel.class);
        when(realm.getClientScopesStream()).thenReturn(Stream.of(scope));
        when(scope.getName()).thenReturn(MigrateTo26_8_2.SCOPE_NAME);
        when(scope.getProtocolMappersStream()).thenReturn(Stream.of(compatibleMapper));
        when(compatibleMapper.getName()).thenReturn("uds-admin-api-audience");
        when(compatibleMapper.getProtocolMapper()).thenReturn("oidc-audience-mapper");
        when(compatibleMapper.getConfig()).thenReturn(new HashMap<>());
        when(realm.getClientByClientId(anyString())).thenAnswer(invocation ->
                "uds-operator".equals(invocation.getArgument(0)) ? operator : null);
        when(operator.getClientScopes(true)).thenReturn(Map.of(MigrateTo26_8_2.SCOPE_NAME, scope));

        new MigrateTo26_8_2().migrate(session, realm);

        verify(scope, never()).addProtocolMapper(any());
        verify(scope).updateProtocolMapper(compatibleMapper);
        verify(compatibleMapper).setProtocolMapper("uds-admin-api-audience-mapper");
        verify(operator, never()).addClientScope(any(), anyBoolean());
    }
}
