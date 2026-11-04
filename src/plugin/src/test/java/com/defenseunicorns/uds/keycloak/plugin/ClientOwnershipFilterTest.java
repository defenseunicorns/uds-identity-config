/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutor;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutorConfiguration;
import org.keycloak.events.Errors;
import org.keycloak.services.clientpolicy.ClientPolicyEvent;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.keycloak.common.Profile;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.function.Executable;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.resources.admin.AdminAuth;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientOwnershipFilterTest {
    private final KeycloakSession session = mock(KeycloakSession.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final ClientProvider clients = mock(ClientProvider.class);
    private final ClientOwnershipFilter filter = new ClientOwnershipFilter(null);
    private final UDSClientPolicyPermissionsExecutor executor = new UDSClientPolicyPermissionsExecutor(session);

    private MockedStatic<Profile> profile;

    @BeforeEach
    void configureProfile() {
        // Exercise Keycloak's role evaluator without bootstrapping fine-grained authorization services.
        profile = mockStatic(Profile.class);
        UDSClientPolicyPermissionsExecutorConfiguration config = new UDSClientPolicyPermissionsExecutorConfiguration();
        config.setUseDefaultAllowedProtocolMappers(false);
        config.setUseDefaultAllowedClientScopes(false);
        executor.setupConfiguration(config);
    }

    @AfterEach
    void restoreProfile() {
        profile.close();
    }

    ClientOwnershipFilterTest() {
        filter.session = session;
    }

    @Test
    void disablesBothFiltersOnlyForExplicitFalse() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        for (String value : new String[] {"false", "FALSE"}) {
            ClientOwnershipFilter disabled = new ClientOwnershipFilter(value);
            disabled.session = session;
            disabled.filter(request);
            disabled.filter(request, response);
        }
        verifyNoInteractions(session, request, response);
    }

    @Test
    void enablesFilteringByDefaultAndForOtherValues() {
        authenticate("application", "service-account-id");
        target("other-application");
        ContainerRequestContext request = request("admin/realms/uds/clients/target/client-secret");
        for (String value : new String[] {null, "true", "TRUE", "", "invalid"}) {
            ClientOwnershipFilter enabled = new ClientOwnershipFilter(value);
            enabled.session = session;
            assertForbidden(() -> enabled.filter(request));
        }
    }

    @Test
    void allowsOwnedSubResourcesForAnApplication() {
        authenticate("application", "service-account-id");
        target("application");
        assertDoesNotThrow(() -> filter.filter(request("admin/realms/uds/clients/target/protocol-mappers/models")));
        verify(clients).getClientById(realm, "target");
    }

    @Test
    void rejectsSubResourcesOwnedByAnotherApplication() {
        // given
        authenticate("application", "service-account-id");
        target("other-application");

        // when / then
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/client-secret")));
    }

    @Test
    void rejectsUnownedSubResources() {
        // given
        authenticate("application", "service-account-id");
        target(null);

        // when / then
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/client-secret")));
    }

    @Test
    void rejectsMissingClients() {
        // given
        authenticate("application", "service-account-id");
        when(session.clients()).thenReturn(clients);

        // when / then
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/client-secret")));
    }

    @Test
    void preservesExistingManagedClientRules() {
        authenticate("uds-operator", null);
        target("other-application");
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/client-secret")));
    }

    @Test
    void leavesUnauthenticatedRequestsToKeycloak() {
        // given
        ContainerRequestContext request = request("admin/realms/uds/clients/target/client-secret");

        // when / then
        assertDoesNotThrow(() -> filter.filter(request));
        verify(session, never()).clients();
    }

    @Test
    void leavesHumanRequestsToKeycloak() {
        // given
        authenticate("admin-cli", null);
        ContainerRequestContext request = request("admin/realms/uds/clients/target/client-secret");

        // when / then
        assertDoesNotThrow(() -> filter.filter(request));
        verify(session, never()).clients();
    }

    @Test
    void leavesClientCrudAndOtherRoutesUnchanged() {
        for (String path : new String[] {
                "admin/realms/uds/clients", "admin/realms/uds/clients/target",
                "realms/uds/protocol/openid-connect/token",
                "other/realms/uds/clients/target/resource",
                "admin/other/uds/clients/target/resource",
                "admin/realms/uds/users/target/credentials"
        }) {
            assertDoesNotThrow(() -> filter.filter(request(path)), path);
        }
        verifyNoInteractions(session);
    }

    @Test
    void preservesOwnedSecretsAndMasksOtherOwnersInLists() {
        // given
        authenticate("application", "service-account-id");
        ClientRepresentation owned = representation("application");
        ClientRepresentation other = representation("other-application");
        ClientRepresentation unowned = representation(null);
        ClientRepresentation withoutAttributes = representation(null);
        withoutAttributes.setAttributes(null);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getEntity()).thenReturn(Stream.of(owned, other, unowned, withoutAttributes, "unchanged"));

        // when
        filter.filter(mock(ContainerRequestContext.class), response);

        // then
        ArgumentCaptor<Object> entity = ArgumentCaptor.forClass(Object.class);
        verify(response).setEntity(entity.capture());
        try (Stream<?> result = (Stream<?>) entity.getValue()) {
            assertEquals(List.of(owned, other, unowned, withoutAttributes, "unchanged"), result.toList());
        }
        assertEquals("test-secret", owned.getSecret());
        assertEquals("test-registration-token", owned.getRegistrationAccessToken());
        assertEquals("test-rotated-secret", owned.getAttributes().get("client.secret.rotated"));
        assertEquals("test-private-key", owned.getAttributes().get("jwt.credential.private.key"));
        for (ClientRepresentation client : new ClientRepresentation[] {other, unowned, withoutAttributes}) {
            assertEquals("**********", client.getSecret());
            assertEquals("**********", client.getRegistrationAccessToken());
            if (client.getAttributes() != null) {
                assertEquals("**********", client.getAttributes().get("client.secret.rotated"));
                assertEquals("**********", client.getAttributes().get("jwt.credential.private.key"));
                assertEquals("unchanged", client.getAttributes().get("description"));
            }
        }
    }

    @Test
    void closesTheOriginalStreamWhenTheFilteredStreamCloses() {
        // given
        authenticate("application", "service-account-id");
        AtomicBoolean closed = new AtomicBoolean();
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getEntity()).thenReturn(Stream.empty().onClose(() -> closed.set(true)));

        // when
        filter.filter(mock(ContainerRequestContext.class), response);
        ArgumentCaptor<Object> entity = ArgumentCaptor.forClass(Object.class);
        verify(response).setEntity(entity.capture());

        // then
        assertFalse(closed.get());
        ((Stream<?>) entity.getValue()).close();
        assertTrue(closed.get());
    }

    @Test
    void leavesHumanListResponsesUnchanged() {
        authenticate("admin-cli", null);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        filter.filter(mock(ContainerRequestContext.class), response);
        verifyNoInteractions(response);
    }

    @Test
    void leavesNonStreamResponsesUnchanged() {
        authenticate("application", "service-account-id");
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getEntity()).thenReturn(Map.of("status", "ok"));
        filter.filter(mock(ContainerRequestContext.class), response);
        verify(response, never()).setEntity(any());
    }

    @Test
    void recognizesOnlyFullAdministrators() {
        for (String role : new String[] {"realm-admin", "admin", "manage-clients", "manage-users"}) {
            // given
            authenticateWithRole(role);

            // when / then
            assertEquals(role.equals("admin") || role.equals("realm-admin"),
                    UDSClientPolicyPermissionsExecutor.isRealmAdmin(session), role);
        }
    }

    @Test
    void allowsFullAdministratorsToAccessNonOwnedSubResources() {
        for (String role : new String[] {"realm-admin", "admin"}) {
            // given
            authenticateWithRole(role);
            ContainerRequestContext request = request("admin/realms/uds/clients/target/client-secret");

            // when / then
            assertDoesNotThrow(() -> filter.filter(request), role);
            verify(session, never()).clients();
        }
    }

    @Test
    void leavesFullAdministratorResponsesUnchanged() {
        for (String role : new String[] {"realm-admin", "admin"}) {
            // given
            authenticateWithRole(role);
            ContainerResponseContext response = mock(ContainerResponseContext.class);

            // when
            filter.filter(mock(ContainerRequestContext.class), response);

            // then
            verifyNoInteractions(response);
        }
    }

    @Test
    void rejectsNonOwnedSubResourcesForLimitedAdministrators() {
        for (String role : new String[] {"manage-clients", "manage-users"}) {
            // given
            authenticateWithRole(role);
            target("other-application");

            // when / then
            assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/client-secret")));
        }
    }

    @Test
    void adminUpdatesPreserveExistingOwnership() throws Exception {
        for (String clientId : new String[] {"admin-cli", "uds-opentofu-client"}) {
            for (String owner : new String[] {null, "another-application"}) {
                // given
                ClientRepresentation proposed = representation(owner);
                ClientCRUDContext context = adminRequest(ClientPolicyEvent.UPDATE, clientId, proposed);

                // when
                executor.executeOnEvent(context);

                // then
                assertEquals(owner, proposed.getAttributes().get("created-by"), clientId);
            }
        }
    }

    @Test
    void adminCreationDoesNotStampOwnership() throws Exception {
        for (String clientId : new String[] {"admin-cli", "uds-opentofu-client"}) {
            // given
            ClientRepresentation proposed = new ClientRepresentation();
            ClientCRUDContext context = adminRequest(ClientPolicyEvent.REGISTER, clientId, proposed);

            // when
            executor.executeOnEvent(context);

            // then
            assertNull(proposed.getAttributes(), clientId);
        }
    }

    @Test
    void adminsCanReadAndDeleteOtherClients() throws Exception {
        for (String clientId : new String[] {"admin-cli", "uds-opentofu-client"}) {
            for (ClientPolicyEvent event : List.of(ClientPolicyEvent.VIEW, ClientPolicyEvent.UNREGISTER)) {
                // given
                ClientCRUDContext context = adminRequest(event, clientId, null);

                // when / then
                assertDoesNotThrow(() -> executor.executeOnEvent(context), clientId + " " + event);
            }
        }
    }

    @Test
    void adminWritesStillValidateProtocolMappers() {
        for (String clientId : new String[] {"admin-cli", "uds-opentofu-client"}) {
            for (ClientPolicyEvent event : List.of(ClientPolicyEvent.REGISTER, ClientPolicyEvent.UPDATE)) {
                // given
                ClientRepresentation proposed = new ClientRepresentation();
                ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
                mapper.setProtocolMapper("disallowed-mapper");
                proposed.setProtocolMappers(List.of(mapper));
                ClientCRUDContext context = adminRequest(event, clientId, proposed);

                // when
                ClientPolicyException error = assertThrows(ClientPolicyException.class,
                        () -> executor.executeOnEvent(context), clientId + " " + event);

                // then
                assertEquals(Errors.INVALID_CLIENT, error.getMessage());
            }
        }
    }

    @Test
    void adminWritesStillValidateClientScopes() {
        for (String clientId : new String[] {"admin-cli", "uds-opentofu-client"}) {
            for (ClientPolicyEvent event : List.of(ClientPolicyEvent.REGISTER, ClientPolicyEvent.UPDATE)) {
                // given
                ClientRepresentation proposed = new ClientRepresentation();
                proposed.setDefaultClientScopes(List.of("disallowed-scope"));
                ClientCRUDContext context = adminRequest(event, clientId, proposed);

                // when
                ClientPolicyException error = assertThrows(ClientPolicyException.class,
                        () -> executor.executeOnEvent(context), clientId + " " + event);

                // then
                assertEquals(Errors.INVALID_CLIENT, error.getMessage());
            }
        }
    }

    @Test
    void excludesOtherRealmsFromBothFilters() {
        authenticate("application", "service-account-id");
        when(realm.getName()).thenReturn("other");
        ContainerRequestContext request = request("admin/realms/other/clients/target/client-secret");
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        filter.filter(request);
        filter.filter(request, response);
        verify(session, never()).clients();
        verifyNoInteractions(response);
    }

    @Test
    void matchesOnlyUdsClientSubResourcePaths() {
        for (String path : new String[] {"", "admin", "admin/realms", "admin/realms/uds",
                "admin/realms/uds/clients", "admin/realms/uds/clients/id",
                "admin/realms/other/clients/id/client-secret", "admin/realms/uds/users/id/credentials"}) {
            assertFalse(ClientOwnershipFilter.isClientSubResource(request(path).getUriInfo().getPathSegments()), path);
        }
        assertTrue(ClientOwnershipFilter.isClientSubResource(
                request("admin/realms/uds/clients/id/client-secret").getUriInfo().getPathSegments()));
        assertTrue(ClientOwnershipFilter.isClientSubResource(
                request("admin/realms/uds/clients/id/protocol-mappers/models").getUriInfo().getPathSegments()));
    }

    private void assertForbidden(Executable action) {
        // The unit-test classpath has the Jakarta API, without a REST server implementation.
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(403);
        when(response.getStatusInfo()).thenReturn(Response.Status.FORBIDDEN);
        Response.ResponseBuilder builder = mock(Response.ResponseBuilder.class);
        when(builder.build()).thenReturn(response);
        try (var responses = mockStatic(Response.class)) {
            responses.when(() -> Response.status(Response.Status.FORBIDDEN)).thenReturn(builder);
            assertThrows(ForbiddenException.class, action);
        }
    }

    private void authenticate(String clientId, String serviceAccountLink) {
        ClientModel application = mock(ClientModel.class);
        when(application.getClientId()).thenReturn(clientId);
        UserModel user = mock(UserModel.class);
        when(user.getServiceAccountClientLink()).thenReturn(serviceAccountLink);
        KeycloakContext context = mock(KeycloakContext.class);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        UserSessionModel userSession = mock(UserSessionModel.class);
        when(context.getUserSession()).thenReturn(userSession);
        when(userSession.getId()).thenReturn("session");
        when(userSession.getUser()).thenReturn(user);
        AccessToken token = new AccessToken();
        token.setSessionId("session");
        when(realm.getName()).thenReturn("uds");
        RealmProvider realms = mock(RealmProvider.class);
        RealmModel master = mock(RealmModel.class);
        when(session.realms()).thenReturn(realms);
        when(realms.getRealmByName("master")).thenReturn(master);
        when(session.getAttribute(anyString(), eq(AdminAuth.class)))
                .thenReturn(new AdminAuth(realm, token, user, application));
    }

    private void authenticateWithRole(String roleName) {
        authenticate("application", "service-account-id");
        grantRole(roleName);
    }

    private void grantRole(String roleName) {
        AdminAuth auth = session.getAttribute("ignored", AdminAuth.class);
        RoleModel granted = mock(RoleModel.class);
        if (roleName.equals("admin")) {
            when(session.realms().getRealmByName("master").getRole("admin")).thenReturn(granted);
        } else {
            ClientModel management = mock(ClientModel.class);
            when(realm.getClientByClientId("realm-management")).thenReturn(management);
            when(management.getRealm()).thenReturn(realm);
            when(management.getRole(roleName)).thenReturn(granted);
        }
        when(auth.getUser().hasRole(granted)).thenReturn(true);
    }

    private ClientCRUDContext adminRequest(ClientPolicyEvent event, String clientId, ClientRepresentation proposed) {
        authenticate(clientId, clientId.equals("admin-cli") ? null : "service-account-id");
        grantRole("realm-admin");
        AdminAuth auth = session.getAttribute("ignored", AdminAuth.class);
        ClientCRUDContext context = mock(ClientCRUDContext.class);
        when(context.getEvent()).thenReturn(event);
        when(context.getAuthenticatedClient()).thenReturn(auth.getClient());
        when(context.getAuthenticatedUser()).thenReturn(auth.getUser());
        when(context.getProposedClientRepresentation()).thenReturn(proposed);
        ClientModel target = target("another-application");
        when(context.getTargetClient()).thenReturn(target);
        return context;
    }

    private ClientModel target(String owner) {
        when(session.clients()).thenReturn(clients);
        ClientModel target = mock(ClientModel.class);
        when(target.getAttribute("created-by")).thenReturn(owner);
        when(clients.getClientById(realm, "target")).thenReturn(target);
        return target;
    }

    private ContainerRequestContext request(String path) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uri = mock(UriInfo.class);
        when(request.getUriInfo()).thenReturn(uri);
        var segments = Stream.of(path.split("/")).map(value -> {
            PathSegment segment = mock(PathSegment.class);
            when(segment.getPath()).thenReturn(value);
            return segment;
        }).toList();
        when(uri.getPathSegments()).thenReturn(segments);
        return request;
    }

    private ClientRepresentation representation(String owner) {
        ClientRepresentation client = new ClientRepresentation();
        client.setSecret("test-secret");
        client.setRegistrationAccessToken("test-registration-token");
        client.setAttributes(new HashMap<>(Map.of(
                "client.secret.rotated", "test-rotated-secret",
                "jwt.credential.private.key", "test-private-key",
                "description", "unchanged")));
        if (owner != null) client.getAttributes().put("created-by", owner);
        return client;
    }
}
