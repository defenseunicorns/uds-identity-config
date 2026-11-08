/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */
package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.*;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientScopePolicyTest {
    @Mock KeycloakSession session;
    @Mock KeycloakContext context;
    @Mock RealmModel realm;
    @Mock ClientScopeModel scope;

    @Test
    void retainsExistingDefaultAdminApiScope() {
        // given
        var client = new ClientRepresentation();
        client.setDefaultClientScopes(List.of("kc-admin-api"));
        // when / then
        assertDoesNotThrow(() -> ClientScopePolicy.validate(client, List.of(), true));
    }

    @Test
    void rejectsNewDefaultAdminApiScope() {
        // given
        var client = new ClientRepresentation();
        client.setDefaultClientScopes(List.of("kc-admin-api"));
        // when / then
        assertThrows(ClientPolicyException.class, () -> ClientScopePolicy.validate(client, List.of(), false));
    }

    @Test
    void retentionDoesNotAllowOptionalAdminApiScope() {
        // given
        var client = new ClientRepresentation();
        client.setOptionalClientScopes(List.of("kc-admin-api"));
        // when / then
        assertThrows(ClientPolicyException.class, () -> ClientScopePolicy.validate(client, List.of(), true));
    }

    @Test
    void nullAllowlistDoesNotRestrictScopes() {
        // given
        var client = new ClientRepresentation();
        client.setDefaultClientScopes(List.of("custom"));
        client.setOptionalClientScopes(List.of("optional"));
        // when / then
        assertDoesNotThrow(() -> ClientScopePolicy.validate(client, null, false));
    }

    @Test
    void absentScopeListsNeedNoAllowances() {
        // given
        var client = new ClientRepresentation();
        // when / then
        assertDoesNotThrow(() -> ClientScopePolicy.validate(client, List.of(), false));
    }

    @Test
    void resolvesDefaultsAndRealmDefaultScopes() {
        // given
        var config = new UDSClientPolicyPermissionsExecutorConfiguration();
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getDefaultClientScopesStream(true)).thenReturn(Stream.of(scope));
        when(scope.getName()).thenReturn("realm-default");
        // when
        var allowed = ClientScopePolicy.resolveAllowed(config, session);
        // then
        assertTrue(allowed.containsAll(ClientScopePolicy.DEFAULT_ALLOWED_CLIENT_SCOPES));
        assertTrue(allowed.contains("realm-default"));
    }

    @Test
    void mergesExplicitScopesWithDefaultsDisabled() {
        // given
        var config = new UDSClientPolicyPermissionsExecutorConfiguration();
        config.setUseDefaultAllowedClientScopes(false);
        config.setAllowedClientScopes(List.of("one", "two"));
        config.setAllowedClientScopesAsString("two, three");
        // when
        var allowed = ClientScopePolicy.resolveAllowed(config, session);
        // then
        assertEquals(Set.of("one", "two", "three"), Set.copyOf(allowed));
        verifyNoInteractions(session);
    }
}
