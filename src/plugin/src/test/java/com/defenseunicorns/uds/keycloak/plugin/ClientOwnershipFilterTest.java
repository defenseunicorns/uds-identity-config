/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutor;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
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

    private MockedStatic<Profile> profile;

    @BeforeEach
    void configureProfile() {
        // Exercise Keycloak's role evaluator without bootstrapping fine-grained authorization services.
        profile = mockStatic(Profile.class);
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
    void requiresMatchingOwnershipForSubResources() {
        authenticate("application", "service-account-id");
        ClientModel target = target("other-application");
        ContainerRequestContext request = request("admin/realms/uds/clients/target/client-secret");
        assertForbidden(() -> filter.filter(request));

        when(target.getAttribute("created-by")).thenReturn(null);
        assertForbidden(() -> filter.filter(request));

        when(clients.getClientById(realm, "target")).thenReturn(null);
        assertForbidden(() -> filter.filter(request));
    }

    @Test
    void preservesExistingManagedClientRules() {
        authenticate("uds-operator", null);
        target("other-application");
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/client-secret")));
    }

    @Test
    void leavesHumanAndUnauthenticatedRequestsToKeycloak() {
        ContainerRequestContext request = request("admin/realms/uds/clients/target/client-secret");
        assertDoesNotThrow(() -> filter.filter(request));
        authenticate("admin-cli", null);
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
    void masksOtherOwnersInListsAndPreservesStreamClosure() {
        authenticate("application", "service-account-id");
        ClientRepresentation owned = representation("application");
        ClientRepresentation other = representation("other-application");
        ClientRepresentation unowned = representation(null);
        ClientRepresentation withoutAttributes = representation(null);
        withoutAttributes.setAttributes(null);
        AtomicBoolean closed = new AtomicBoolean();
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getEntity()).thenReturn(Stream.of(owned, other, unowned, withoutAttributes, "unchanged")
                .onClose(() -> closed.set(true)));

        filter.filter(mock(ContainerRequestContext.class), response);
        ArgumentCaptor<Object> entity = ArgumentCaptor.forClass(Object.class);
        verify(response).setEntity(entity.capture());
        assertFalse(closed.get());
        try (Stream<?> result = (Stream<?>) entity.getValue()) {
            assertEquals(List.of(owned, other, unowned, withoutAttributes, "unchanged"), result.toList());
        }
        assertTrue(closed.get());
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
    void recognizesOnlyFullAdministratorsAndPreservesTheirResponses() throws Exception {
        for (String roleName : new String[] {"realm-admin", "admin", "manage-clients", "manage-users"}) {
            authenticate("application", "service-account-id");
            target("other-application");
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
            boolean fullAdmin = roleName.equals("admin") || roleName.equals("realm-admin");
            assertEquals(fullAdmin, UDSClientPolicyPermissionsExecutor.isRealmAdmin(session));
            ContainerRequestContext request = request("admin/realms/uds/clients/target/client-secret");
            if (fullAdmin) {
                ClientCRUDContext crud = mock(ClientCRUDContext.class);
                new UDSClientPolicyPermissionsExecutor(session).executeOnEvent(crud);
                verifyNoInteractions(crud);
                assertDoesNotThrow(() -> filter.filter(request));
                ContainerResponseContext response = mock(ContainerResponseContext.class);
                filter.filter(request, response);
                verifyNoInteractions(response);
            } else {
                assertForbidden(() -> filter.filter(request));
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
