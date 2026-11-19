/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */
package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.representations.idm.ClientPoliciesRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.ClientPolicyManager;
import org.keycloak.util.JsonSerialization;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientOwnershipPolicyTest {
    @Mock
    private KeycloakSession session;
    @Mock
    private RealmModel realm;
    @Mock
    private ClientPolicyManager manager;
    @Mock
    private KeycloakContext context;

    @BeforeEach
    void setUp() throws Exception {
        // The metadata-only test does not read realm profiles.
        lenient().when(session.getContext()).thenReturn(context);
        lenient().when(context.getRealm()).thenReturn(realm);
        lenient().when(session.clientPolicy()).thenReturn(manager);
        lenient().when(manager.getClientPolicies(realm, false)).thenReturn(JsonSerialization.readValue(
                "{\"policies\":[{\"enabled\":true,\"profiles\":[\"profile-0\",\"profile-1\"]}]}",
                ClientPoliciesRepresentation.class));
    }

    @Test
    void oldProfilesPreserveDefaultUdsCallers() throws Exception {
        // given
        for (String configuration : List.of("", ",\"configuration\":null", ",\"configuration\":{}")) {
            profiles("{\"profiles\":[{\"executors\":[{\"executor\":\"uds-operator-permissions\"" + configuration + "}]}]}");
            // when / then
            assertTrue(ClientOwnershipPolicy.isManagedClient(session, "uds-operator"));
            assertTrue(ClientOwnershipPolicy.isManagedClient(session, "uds-fleet-admin"));
            assertFalse(ClientOwnershipPolicy.isManagedClient(session, "uds-opentofu-client"));
            assertFalse(ClientOwnershipPolicy.isManagedClient(session, "application"));
            assertFalse(ClientOwnershipPolicy.isManagedClient(session, null));
            assertFalse(ClientOwnershipPolicy.isManagedClient(session, " "));
        }
    }

    @Test
    void readsChangesWithoutRestartAndDoesNotUseUnrelatedExecutors() throws Exception {
        // given
        profiles("""
                {"profiles":[{"executors":[
                  {"executor":"other","configuration":{"ownership-enforced-clients":["ignored"]}},
                  {"executor":"uds-operator-permissions","configuration":{"ownership-enforced-clients":[" application "]}}
                ]}]}
                """);
        // when / then
        assertTrue(ClientOwnershipPolicy.isManagedClient(session, "application"));
        assertFalse(ClientOwnershipPolicy.isManagedClient(session, "app"));
        assertFalse(ClientOwnershipPolicy.isManagedClient(session, "ignored"));
        assertFalse(ClientOwnershipPolicy.isManagedClient(session, "uds-operator"));
        // given
        profiles("{\"profiles\":[{\"executors\":[{\"executor\":\"uds-operator-permissions\","
                + "\"configuration\":{\"ownership-enforced-clients\":[]}}]}]}");
        // when / then
        assertFalse(ClientOwnershipPolicy.isManagedClient(session, "application"));
        assertFalse(ClientOwnershipPolicy.isManagedClient(session, "uds-operator"));
    }

    @Test
    void unionsUdsProfilesAndHandlesMissingProfiles() throws Exception {
        // given
        profiles("""
                {"profiles":[
                  {"executors":[{"executor":"uds-operator-permissions","configuration":{"ownership-enforced-clients":["first"]}}]},
                  {"executors":[{"executor":"uds-operator-permissions","configuration":{"ownership-enforced-clients":["second"]}}]}
                ]}
                """);
        // when / then
        assertTrue(ClientOwnershipPolicy.isManagedClient(session, "first"));
        assertTrue(ClientOwnershipPolicy.isManagedClient(session, "second"));
        for (String emptyProfiles : List.of("{}", "{\"profiles\":null}", "{\"profiles\":[{\"name\":\"empty\"}]}")) {
            // given
            profiles(emptyProfiles);
            // when / then
            assertFalse(ClientOwnershipPolicy.isManagedClient(session, "first"));
        }
    }

    @Test
    void detachedProfilesDoNotEnrollCallers() throws Exception {
        // given
        profiles("""
                {"profiles":[
                  {"executors":[{"executor":"uds-operator-permissions","configuration":{"ownership-enforced-clients":[]}}]},
                  {"executors":[{"executor":"uds-operator-permissions","configuration":{"ownership-enforced-clients":["application"]}}]}
                ]}
                """);
        when(manager.getClientPolicies(realm, false)).thenReturn(JsonSerialization.readValue(
                "{\"policies\":[{\"enabled\":true,\"profiles\":[\"profile-0\"]}]}",
                ClientPoliciesRepresentation.class));

        // when / then
        assertFalse(ClientOwnershipPolicy.isManagedClient(session, "application"));
    }

    @Test
    void configurationFailuresDoNotBypassEnforcement() throws Exception {
        // given
        when(manager.getClientProfiles(realm, false)).thenThrow(new ClientPolicyException("invalid profiles"));
        // when / then
        assertThrows(IllegalStateException.class, () -> ClientOwnershipPolicy.isManagedClient(session, "application"));
    }

    @Test
    void uiAndJsonUseTheSameBackwardCompatibleDefaults() throws Exception {
        // given
        var property = new UDSClientPolicyPermissionsExecutorFactory().getConfigProperties().stream()
                .filter(p -> p.getName().equals("ownership-enforced-clients")).findFirst().orElseThrow();
        // when
        var config = JsonSerialization.readValue("{}", UDSClientPolicyPermissionsExecutorConfiguration.class);
        // then
        assertEquals(ProviderConfigProperty.MULTIVALUED_STRING_TYPE, property.getType());
        assertEquals(List.of("uds-operator", "uds-fleet-admin"), config.getOwnershipEnforcedClients());
        assertEquals(config.getOwnershipEnforcedClients(), property.getDefaultValue());
        // when
        var edited = JsonSerialization.readValue("{\"ownership-enforced-clients\":[\"application\"]}",
                UDSClientPolicyPermissionsExecutorConfiguration.class);
        // then
        assertTrue(edited.enforcesOwnershipFor("application"));
        assertFalse(edited.enforcesOwnershipFor("uds-operator"));
    }

    private void profiles(String json) throws Exception {
        var profiles = JsonSerialization.readValue(json, ClientProfilesRepresentation.class);
        if (profiles.getProfiles() != null) {
            for (int i = 0; i < profiles.getProfiles().size(); i++) {
                profiles.getProfiles().get(i).setName("profile-" + i);
            }
        }
        when(manager.getClientProfiles(realm, false)).thenReturn(profiles);
    }
}
