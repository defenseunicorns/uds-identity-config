/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.defenseunicorns.uds.keycloak.plugin.CustomGroupPathMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Errors;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.mappers.HardcodedClaim;
import org.keycloak.protocol.saml.mappers.UserAttributeStatementMapper;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UDSClientPolicyPermissionsExecutorTest {

    final UDSClientPolicyPermissionsExecutor executor = new UDSClientPolicyPermissionsExecutor(null);

    @Mock
    KeycloakSession session;

    @Mock
    KeycloakContext keycloakContext;

    @Mock
    RealmModel realm;

    @Mock
    KeycloakSessionFactory factory;

    @Mock
    ClientModel client;

    @BeforeEach
    public void before() {
        UDSClientPolicyPermissionsExecutorConfiguration config = new UDSClientPolicyPermissionsExecutorConfiguration();
        config.setUseDefaultAllowedProtocolMappers(false);
        config.setAllowedProtocolMappers(UDSClientPolicyPermissionsExecutor.DEFAULT_ALLOWED_PROTOCOL_MAPPERS);
        config.setUseDefaultAllowedClientScopes(false);
        config.setAllowedClientScopes(UDSClientPolicyPermissionsExecutor.DEFAULT_ALLOWED_CLIENT_SCOPES);
        executor.setupConfiguration(config);
    }

    @Test
    public void shouldReportAsOwnedByUDS() {
        // given
        when(client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_UDS_OPERATOR))
                .thenReturn(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_UDS_OPERATOR_VALUE);

        // when
        boolean result = executor.isOwnedByUDSOperator(client);

        // then
        assertTrue(result);
    }

    @Test
    public void shouldNotReportAsOwnedByUDS() {
        // given
        when(client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_UDS_OPERATOR)).thenReturn(null);

        // when
        boolean result = executor.isOwnedByUDSOperator(client);

        // then
        assertFalse(result);
    }

    @Test
    public void shouldNotThrowExceptionWhenAuthenticatedClientIsNull() {
        // given
        ClientCRUDContext clientCRUDContext = mock(ClientCRUDContext.class);

        // when
        String result = executor.getAuthenticatedClientId(clientCRUDContext);

        // then
        assertNull(result);
    }

    @Test
    public void shouldValidateFullScopeDisabled() {
        // given
        ClientRepresentation rep = new ClientRepresentation();
        rep.setFullScopeAllowed(true);

        // when/then
        assertThrows(ClientPolicyException.class, () -> {
            executor.validateClientSettings(rep);
        });
    }

    @Test
    public void shouldEnforceWhitelistedProtocolMappers() {
        // given
        ProtocolMapperRepresentation allowedProtocolMapper = new ProtocolMapperRepresentation() {{
            setProtocolMapper(UserAttributeStatementMapper.PROVIDER_ID);
        }};

        ProtocolMapperRepresentation disallowedProtocolMapper = new ProtocolMapperRepresentation() {{
            setProtocolMapper("invalid");
        }};

        ClientRepresentation rep = new ClientRepresentation();
        // We need a modifiable List implementation here - that's why it's wrapped with an ArrayList
        rep.setProtocolMappers(new ArrayList<>(List.of(allowedProtocolMapper, disallowedProtocolMapper)));

        // when
        try {
            executor.validateClientSettings(rep);
            fail("Expected ClientPolicyException");
        } catch (ClientPolicyException e) {
            assertEquals(Errors.INVALID_CLIENT, e.getMessage());
            assertEquals("The Protocol Mapper invalid is not allowed. Rejecting request.", e.getErrorDetail());
        }
    }

    @Test
    public void shouldEnforceWhitelistedCustomClaims() {
        // given
        ClientRepresentation rep = new ClientRepresentation();
        // We need a modifiable List implementation here - that's why it's wrapped with an ArrayList
        rep.setDefaultClientScopes(new ArrayList<>(List.of(CustomGroupPathMapper.GROUPS_CLAIM, "invalid")));
        rep.setOptionalClientScopes(new ArrayList<>(List.of(CustomGroupPathMapper.GROUPS_CLAIM, "invalid")));

        // when
        try {
            executor.validateClientSettings(rep);
            fail("Expected ClientPolicyException");
        } catch (ClientPolicyException e) {
            assertEquals(Errors.INVALID_CLIENT, e.getMessage());
            assertEquals("The Client Scope invalid is not allowed. Rejecting request.", e.getErrorDetail());
        }
    }

    @Test
    public void shouldConfigureWithDefaults() {
        // given
        class TestProtocolMapper extends HardcodedClaim{}

        UDSClientPolicyPermissionsExecutor executor = new UDSClientPolicyPermissionsExecutor(session);
        UDSClientPolicyPermissionsExecutorConfiguration config = new UDSClientPolicyPermissionsExecutorConfiguration();
        config.setUseDefaultAllowedProtocolMappers(true);
        config.setUseDefaultAllowedClientScopes(true);

        {
            doReturn(factory).when(session).getKeycloakSessionFactory();
            doReturn(Stream.of(new TestProtocolMapper())).when(factory).getProviderFactoriesStream(any());
        }

        {
            doReturn(keycloakContext).when(session).getContext();
            doReturn(realm).when(keycloakContext).getRealm();

            ClientScopeModel additionalScope = mock(ClientScopeModel.class);
            doReturn("New Claim").when(additionalScope).getName();

            doReturn(Stream.of(additionalScope)).when(realm).getDefaultClientScopesStream(anyBoolean());
        }

        // when
        executor.setupConfiguration(config);
        UDSClientPolicyPermissionsExecutorConfiguration resultingConfiguration = executor.configuration;

        // then
        assertTrue(resultingConfiguration.isUseDefaultAllowedProtocolMappers());
        assertEquals(UDSClientPolicyPermissionsExecutor.DEFAULT_ALLOWED_PROTOCOL_MAPPERS.size() + 1, resultingConfiguration.getAllowedProtocolMappers().size());

        assertTrue(resultingConfiguration.isUseDefaultAllowedClientScopes());
        assertEquals(UDSClientPolicyPermissionsExecutor.DEFAULT_ALLOWED_CLIENT_SCOPES.size() + 1, resultingConfiguration.getAllowedClientScopes().size());
    }

    @Test
    public void shouldAddStringifiedProtocolMappersAndClientScopes() {
        //given
        UDSClientPolicyPermissionsExecutorConfiguration config = new UDSClientPolicyPermissionsExecutorConfiguration();
        config.setUseDefaultAllowedProtocolMappers(false);
        config.setUseDefaultAllowedClientScopes(false);
        config.setAllowedProtocolMappersAsString("mapper1 , mapper2 , mapper3"); //intentional test of trimming whitespaces
        config.setAllowedClientScopesAsString("scope1, scope2 ,scope3");

        //when
        executor.setupConfiguration(config);

        //then
        assertEquals(3, executor.configuration.getAllowedProtocolMappers().size());
        assertTrue(executor.configuration.getAllowedProtocolMappers().contains("mapper1"));
        assertTrue(executor.configuration.getAllowedProtocolMappers().contains("mapper2"));
        assertTrue(executor.configuration.getAllowedProtocolMappers().contains("mapper3"));
        assertEquals(3, executor.configuration.getAllowedClientScopes().size());
        assertTrue(executor.configuration.getAllowedClientScopes().contains("scope1"));
        assertTrue(executor.configuration.getAllowedClientScopes().contains("scope2"));
        assertTrue(executor.configuration.getAllowedClientScopes().contains("scope3"));
    }
}