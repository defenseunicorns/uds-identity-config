/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyManager;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.util.JsonSerialization;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.defenseunicorns.uds.keycloak.plugin.ClientOwnershipFilterFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientOwnershipFilterTest {
    private final KeycloakSession session = mock(KeycloakSession.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final ClientProvider clients = mock(ClientProvider.class);
    private final ClientOwnershipFilterFixture fixture = new ClientOwnershipFilterFixture(session, realm, clients);
    private final ClientOwnershipFilter filter = new ClientOwnershipFilter("true");
    ClientOwnershipFilterTest() throws Exception {
        filter.session = session;
        configureOwnership("{}");
    }

    private void configureOwnership(String configuration) throws Exception {
        var manager = mock(ClientPolicyManager.class);
        when(session.clientPolicy()).thenReturn(manager);
        var profiles = JsonSerialization.readValue("{\"profiles\":[{\"name\":\"UDS Client Profile\","
                + "\"executors\":[{\"executor\":\"uds-operator-permissions\",\"configuration\":"
                + configuration + "}]}]}", ClientProfilesRepresentation.class);
        when(manager.getClientProfiles(realm, false)).thenReturn(profiles);
    }

    @Test
    void disablesBothFiltersUnlessExplicitlyEnabled() {
        // given
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        for (String value : new String[] {null, "false", "FALSE", "", "invalid"}) {
            ClientOwnershipFilter disabled = new ClientOwnershipFilter(value);
            disabled.session = session;
            // when
            disabled.filter(request);
            disabled.filter(request, response);
        }
        // then
        verifyNoInteractions(session, request, response);
    }

    @Test
    void enablesFilteringOnlyForExplicitTrue() {
        // given
        fixture.authenticate("uds-operator");
        fixture.target("other-application");
        ContainerRequestContext request = request("admin/realms/uds/clients/target/roles");
        for (String value : new String[] {"true", "TRUE"}) {
            ClientOwnershipFilter enabled = new ClientOwnershipFilter(value);
            enabled.session = session;
            // when / then
            assertForbidden(() -> enabled.filter(request));
        }
    }

    @Test
    void allowsOwnedSubResourcesForAnApplication() {
        // given
        fixture.authenticate("uds-operator");
        fixture.target("uds-operator");
        // when / then
        assertDoesNotThrow(() -> filter.filter(request("admin/realms/uds/clients/target/protocol-mappers/models")));
        verify(clients).getClientById(realm, "target");
    }

    @Test
    void rejectsSubResourcesOwnedByAnotherApplication() {
        // given
        fixture.authenticate("uds-operator");
        fixture.target("other-application");

        // when / then
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/roles")));
    }

    @Test
    void rejectsUnownedSubResources() {
        // given
        fixture.authenticate("uds-operator");
        fixture.target(null);

        // when / then
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/roles")));
    }

    @Test
    void rejectsMissingClients() {
        // given
        fixture.authenticate("uds-operator");
        when(session.clients()).thenReturn(clients);

        // when / then
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/roles")));
    }

    @Test
    void preservesExistingManagedClientRules() {
        // given
        fixture.authenticate("uds-operator");
        fixture.target("other-application");
        // when / then
        assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/roles")));
    }

    @Test
    void leavesUnauthenticatedRequestsToKeycloak() {
        // given
        ContainerRequestContext request = request("admin/realms/uds/clients/target/roles");

        // when / then
        assertDoesNotThrow(() -> filter.filter(request));
        verify(session, never()).clients();
    }

    @Test
    void leavesHumanRequestsToKeycloak() {
        // given
        fixture.authenticate("admin-cli");
        ContainerRequestContext request = request("admin/realms/uds/clients/target/roles");

        // when / then
        assertDoesNotThrow(() -> filter.filter(request));
        verify(session, never()).clients();
    }

    @Test
    void leavesClientCrudAndOtherRoutesUnchanged() {
        // given
        for (String path : new String[] {
                "admin/realms/uds/clients", "admin/realms/uds/clients/target",
                "realms/uds/protocol/openid-connect/token",
                "other/realms/uds/clients/target/resource",
                "admin/other/uds/clients/target/resource",
                "admin/realms/uds/users/target/credentials"
        }) {
            // when / then
            assertDoesNotThrow(() -> filter.filter(request(path)), path);
        }
        verifyNoInteractions(session);
    }

    @Test
    void preservesOwnedSecretsAndMasksOtherOwnersInLists() {
        // given
        fixture.authenticate("uds-operator");
        ClientRepresentation owned = representation("uds-operator");
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
        fixture.authenticate("uds-operator");
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
        // given
        fixture.authenticate("admin-cli");
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        // when
        filter.filter(mock(ContainerRequestContext.class), response);
        // then
        verifyNoInteractions(response);
    }

    @Test
    void leavesNonStreamResponsesUnchanged() {
        // given
        fixture.authenticate("uds-operator");
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getEntity()).thenReturn(Map.of("status", "ok"));
        // when
        filter.filter(mock(ContainerRequestContext.class), response);
        // then
        verify(response, never()).setEntity(any());
    }

    @Test
    void excludesOtherRealmsFromBothFilters() {
        // given
        fixture.authenticate("uds-operator");
        when(realm.getName()).thenReturn("other");
        ContainerRequestContext request = request("admin/realms/other/clients/target/roles");
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        // when
        filter.filter(request);
        filter.filter(request, response);
        // then
        verify(session, never()).clients();
        verifyNoInteractions(response);
    }

    @Test
    void matchesOnlyUdsOwnershipResourcePaths() {
        // given
        for (String path : new String[] {"", "admin", "admin/realms", "admin/realms/uds",
                "admin/realms/uds/clients", "admin/realms/uds/clients/id",
                "admin/realms/other/clients/id/roles", "admin/realms/uds/users/id/credentials",
                "admin/realms/uds/roles-by-id", "admin/realms/other/roles-by-id/id"}) {
            // when / then
            assertFalse(ClientOwnershipFilter.isOwnershipResource(request(path).getUriInfo().getPathSegments()), path);
        }
        assertTrue(ClientOwnershipFilter.isOwnershipResource(
                request("admin/realms/uds/clients/id/roles").getUriInfo().getPathSegments()));
        assertTrue(ClientOwnershipFilter.isOwnershipResource(
                request("admin/realms/uds/clients/id/protocol-mappers/models").getUriInfo().getPathSegments()));
    }

    @Test
    void allowsOwnedClientRolesById() {
        // given
        fixture.authenticate("uds-operator");
        RoleModel role = mock(RoleModel.class);
        when(realm.getRoleById("role")).thenReturn(role);
        ClientModel client = fixture.target("uds-operator");
        when(role.getContainer()).thenReturn(client);

        for (String suffix : List.of("", "/composites", "/management/permissions")) {
            // when / then
            assertDoesNotThrow(() -> filter.filter(request("admin/realms/uds/roles-by-id/role" + suffix)));
        }
    }

    @Test
    void rejectsForeignAndUnownedClientRolesById() {
        // given
        fixture.authenticate("uds-operator");
        RoleModel role = mock(RoleModel.class);
        when(realm.getRoleById("role")).thenReturn(role);
        for (String owner : new String[] {"other-application", null}) {
            ClientModel client = fixture.target(owner);
            when(role.getContainer()).thenReturn(client);
            for (String suffix : List.of("", "/composites", "/management/permissions")) {
                // when / then
                assertForbidden(() -> filter.filter(request("admin/realms/uds/roles-by-id/role" + suffix)));
            }
        }
    }

    @Test
    void leavesRealmAndMissingRolesToKeycloak() {
        // given
        fixture.authenticate("uds-operator");
        RoleModel role = mock(RoleModel.class);
        when(realm.getRoleById("realm-role")).thenReturn(role);
        when(role.getContainer()).thenReturn(realm);

        // when / then
        assertDoesNotThrow(() -> filter.filter(request("admin/realms/uds/roles-by-id/realm-role")));
        assertDoesNotThrow(() -> filter.filter(request("admin/realms/uds/roles-by-id/missing")));
        verify(session, never()).clients();
    }

    @Test
    void enrollmentControlsBothFilterPhasesWithoutAdoptingLegacyClients() throws Exception {
        // given
        fixture.authenticate("application");
        ContainerRequestContext request = request("admin/realms/uds/clients/target/roles");
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        // when
        filter.filter(request);
        filter.filter(request, response);
        // then
        verifyNoInteractions(clients, response);

        // given
        configureOwnership("{\"ownership-enforced-clients\":[\" application \"]}");
        ClientModel target = fixture.target(null);
        ClientRepresentation legacy = representation(null);
        when(response.getEntity()).thenReturn(Stream.of(legacy));
        // when / then
        assertForbidden(() -> filter.filter(request));
        filter.filter(request, response);
        ArgumentCaptor<Object> entity = ArgumentCaptor.forClass(Object.class);
        verify(response).setEntity(entity.capture());
        ((Stream<?>) entity.getValue()).toList();
        assertEquals("**********", legacy.getSecret());
        assertNull(legacy.getAttributes().get("created-by"));
        verify(target, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void defaultCallersRemainProtectedWithoutEnrollment() {
        // given
        for (String caller : List.of("uds-operator", "uds-fleet-admin", "uds-opentofu-client")) {
            fixture.authenticate(caller);
            fixture.target("another-owner");
            // when / then
            assertForbidden(() -> filter.filter(request("admin/realms/uds/clients/target/roles")));
        }
    }

    @Test
    void missingClientIdDoesNotEnrollTheCaller() {
        // given
        fixture.authenticate(null);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        // when
        filter.filter(request("admin/realms/uds/clients/target/roles"));
        filter.filter(mock(ContainerRequestContext.class), response);
        // then
        verifyNoInteractions(clients, response);
    }

}
