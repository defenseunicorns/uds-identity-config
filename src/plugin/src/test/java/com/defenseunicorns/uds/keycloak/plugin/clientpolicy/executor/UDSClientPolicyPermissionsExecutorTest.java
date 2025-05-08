/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.defenseunicorns.uds.keycloak.plugin.CustomGroupPathMapper;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.events.Errors;
import org.keycloak.models.*;
import org.keycloak.protocol.saml.mappers.UserAttributeStatementMapper;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UDSClientPolicyPermissionsExecutorTest extends TestCase {

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

    @Before
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

    @Test(expected = ClientPolicyException.class)
    public void shouldValidateFullScopeDisabled() throws Exception {
        // given
        ClientRepresentation rep = new ClientRepresentation();
        rep.setFullScopeAllowed(true);

        // when
        executor.validateClientSettings(rep);
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
        UDSClientPolicyPermissionsExecutor executor = new UDSClientPolicyPermissionsExecutor(session);
        UDSClientPolicyPermissionsExecutorConfiguration config = new UDSClientPolicyPermissionsExecutorConfiguration();
        config.setUseDefaultAllowedProtocolMappers(true);
        config.setUseDefaultAllowedClientScopes(true);

        {
            doReturn(factory).when(session).getKeycloakSessionFactory();
            doReturn(Stream.of(new CustomGroupPathMapper())).when(factory).getProviderFactoriesStream(any());
        }

        {
            doReturn(keycloakContext).when(session).getContext();
            doReturn(realm).when(keycloakContext).getRealm();

            ClientScopeModel additionalScope = mock(ClientScopeModel.class);
            doReturn(CustomGroupPathMapper.GROUPS_CLAIM).when(additionalScope).getName();

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
}