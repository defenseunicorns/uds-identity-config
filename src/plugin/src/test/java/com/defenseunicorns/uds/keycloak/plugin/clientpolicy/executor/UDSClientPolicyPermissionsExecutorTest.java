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
import org.keycloak.services.clientpolicy.ClientPolicyEvent;
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
    public void shouldReportAsOwnedByMatchingClient() {
        // given
        when(client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY))
                .thenReturn("uds-operator");

        // when
        boolean result = executor.isOwnedBy(client, "uds-operator");

        // then
        assertTrue(result);
    }

    @Test
    public void shouldNotReportAsOwnedWhenAttributeMissing() {
        // given
        when(client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY)).thenReturn(null);

        // when
        boolean result = executor.isOwnedBy(client, "uds-operator");

        // then
        assertFalse(result);
    }

    @Test
    public void shouldNotReportAsOwnedWhenCreatedByDifferentClient() {
        // given: a client created by uds-operator is not owned by uds-fleet-admin
        when(client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY)).thenReturn("uds-operator");

        // when
        boolean result = executor.isOwnedBy(client, "uds-fleet-admin");

        // then
        assertFalse(result);
    }

    @Test
    public void shouldRequireFleetPrefixForFleetAdmin() {
        // given
        ClientRepresentation rep = new ClientRepresentation();
        rep.setClientId("not-fleet");

        // when/then
        try {
            executor.validateClientIdPrefix(rep, "uds-fleet-admin");
            fail("Expected ClientPolicyException");
        } catch (ClientPolicyException e) {
            assertEquals(Errors.INVALID_CLIENT, e.getMessage());
            assertEquals("The Client ID must start with \"fleet-\". Rejecting request.", e.getErrorDetail());
        }
    }

    @Test
    public void shouldAllowFleetPrefixedClientForFleetAdmin() throws ClientPolicyException {
        // given
        ClientRepresentation rep = new ClientRepresentation();
        rep.setClientId("fleet-agent-123");

        // when/then: no exception
        executor.validateClientIdPrefix(rep, "uds-fleet-admin");
    }

    @Test
    public void shouldNotConstrainClientIdForUdsOperator() throws ClientPolicyException {
        // given: uds-operator has an empty prefix, so any clientId is allowed
        ClientRepresentation rep = new ClientRepresentation();
        rep.setClientId("anything-goes");

        // when/then: no exception
        executor.validateClientIdPrefix(rep, "uds-operator");
    }

    @Test
    public void shouldRequireFleetPrefixWhenFleetAdminUpdatesClient() {
        // given
        ClientCRUDContext clientCRUDContext = mock(ClientCRUDContext.class);
        ClientModel fleetAdminClient = mock(ClientModel.class);
        ClientModel targetClient = mock(ClientModel.class);
        ClientRepresentation proposedRep = new ClientRepresentation();
        proposedRep.setClientId("not-fleet");

        when(clientCRUDContext.getAuthenticatedClient()).thenReturn(fleetAdminClient);
        when(fleetAdminClient.getClientId()).thenReturn("uds-fleet-admin");
        when(clientCRUDContext.getEvent()).thenReturn(ClientPolicyEvent.UPDATE);
        when(clientCRUDContext.getTargetClient()).thenReturn(targetClient);
        when(targetClient.getClientId()).thenReturn("fleet-agent-123");
        when(targetClient.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY)).thenReturn("uds-fleet-admin");
        when(clientCRUDContext.getProposedClientRepresentation()).thenReturn(proposedRep);

        // when/then
        try {
            executor.executeOnEvent(clientCRUDContext);
            fail("Expected ClientPolicyException");
        } catch (ClientPolicyException e) {
            assertEquals(Errors.INVALID_CLIENT, e.getMessage());
            assertEquals("The Client ID must start with \"fleet-\". Rejecting request.", e.getErrorDetail());
        }
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

    // ---- executeOnEvent wiring: the switch that connects auth -> event -> enforcement/stamping ----

    private ClientCRUDContext crudContext(ClientPolicyEvent event, String authenticatedClientId) {
        ClientCRUDContext context = mock(ClientCRUDContext.class);
        ClientModel authenticatedClient = mock(ClientModel.class);
        lenient().when(authenticatedClient.getClientId()).thenReturn(authenticatedClientId);
        lenient().when(context.getAuthenticatedClient()).thenReturn(authenticatedClient);
        lenient().when(context.getEvent()).thenReturn(event);
        return context;
    }

    @Test
    public void shouldStampCreatedByOnRegisterForFleetAdmin() throws ClientPolicyException {
        // given
        ClientCRUDContext context = crudContext(ClientPolicyEvent.REGISTER, "uds-fleet-admin");
        ClientRepresentation proposed = new ClientRepresentation();
        proposed.setClientId("fleet-agent-1");
        when(context.getProposedClientRepresentation()).thenReturn(proposed);

        // when
        executor.executeOnEvent(context);

        // then: the plugin stamps ownership with the authenticated client id
        assertEquals("uds-fleet-admin", proposed.getAttributes().get(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY));
    }

    @Test
    public void shouldStampCreatedByOnRegisterForUdsOperator() throws ClientPolicyException {
        // given: uds-operator keeps behaving as before - stamps created-by=uds-operator, no prefix constraint
        ClientCRUDContext context = crudContext(ClientPolicyEvent.REGISTER, "uds-operator");
        ClientRepresentation proposed = new ClientRepresentation();
        proposed.setClientId("anything-goes");
        when(context.getProposedClientRepresentation()).thenReturn(proposed);

        // when
        executor.executeOnEvent(context);

        // then
        assertEquals("uds-operator", proposed.getAttributes().get(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY));
    }

    @Test
    public void shouldOverwriteForgedCreatedByOnRegister() throws ClientPolicyException {
        // given: a caller tries to pre-stamp ownership to a different client
        ClientCRUDContext context = crudContext(ClientPolicyEvent.REGISTER, "uds-fleet-admin");
        ClientRepresentation proposed = new ClientRepresentation();
        proposed.setClientId("fleet-agent-2");
        proposed.setAttributes(new java.util.HashMap<>(java.util.Map.of(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY, "uds-operator")));
        when(context.getProposedClientRepresentation()).thenReturn(proposed);

        // when
        executor.executeOnEvent(context);

        // then: the forged value is overwritten with the actual authenticated client
        assertEquals("uds-fleet-admin", proposed.getAttributes().get(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY));
    }

    @Test
    public void shouldRejectRegisterWithoutFleetPrefixForFleetAdmin() {
        // given
        ClientCRUDContext context = crudContext(ClientPolicyEvent.REGISTER, "uds-fleet-admin");
        ClientRepresentation proposed = new ClientRepresentation();
        proposed.setClientId("not-fleet");
        when(context.getProposedClientRepresentation()).thenReturn(proposed);

        // when/then
        try {
            executor.executeOnEvent(context);
            fail("Expected ClientPolicyException");
        } catch (ClientPolicyException e) {
            assertEquals(Errors.INVALID_CLIENT, e.getMessage());
            assertEquals("The Client ID must start with \"fleet-\". Rejecting request.", e.getErrorDetail());
        }
    }

    @Test
    public void shouldRejectUpdateOfClientNotOwnedByRequester() {
        // given: uds-fleet-admin tries to update a client it does not own
        ClientCRUDContext context = crudContext(ClientPolicyEvent.UPDATE, "uds-fleet-admin");
        ClientModel target = mock(ClientModel.class);
        when(target.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY)).thenReturn("uds-operator");
        when(context.getTargetClient()).thenReturn(target);

        // when/then
        try {
            executor.executeOnEvent(context);
            fail("Expected ClientPolicyException");
        } catch (ClientPolicyException e) {
            assertEquals(Errors.UNAUTHORIZED_CLIENT, e.getMessage());
            assertEquals("The Client doesn't have the created-by=uds-fleet-admin attribute. Rejecting request.", e.getErrorDetail());
        }
    }

    @Test
    public void shouldRejectDeleteOfClientWithNoOwner() {
        // given: built-in client with no created-by attribute
        ClientCRUDContext context = crudContext(ClientPolicyEvent.UNREGISTER, "uds-fleet-admin");
        ClientModel target = mock(ClientModel.class);
        when(target.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY)).thenReturn(null);
        when(context.getTargetClient()).thenReturn(target);

        // when/then
        try {
            executor.executeOnEvent(context);
            fail("Expected ClientPolicyException");
        } catch (ClientPolicyException e) {
            assertEquals(Errors.UNAUTHORIZED_CLIENT, e.getMessage());
        }
    }

    @Test
    public void shouldDoNothingForUnmanagedAuthenticatedClient() throws ClientPolicyException {
        // given: a client that is not in MANAGED_CLIENT_ID_PREFIXES
        ClientCRUDContext context = crudContext(ClientPolicyEvent.REGISTER, "some-other-client");

        // when: no enforcement runs, so the proposed representation is never even read
        executor.executeOnEvent(context);

        // then: no exception, and the executor never touched the proposed client
        verify(context, never()).getProposedClientRepresentation();
    }
}
