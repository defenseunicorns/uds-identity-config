/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.util.JsonSerialization;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ProtocolMapperDelegation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolMapperDelegationTest {
    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    private final Map<String, String> attributes = new HashMap<>();

    @Test
    void tracksOnlyNewlyAddedTypes() throws Exception {
        // given
        stubAttributes();
        attributes.put(ALLOWED_MAPPERS_ATTRIBUTE, "manual,shared");

        // when
        ProtocolMapperDelegation.sync(session, realm, profiles("shared", "managed"));

        // then
        assertEquals("managed,manual,shared", attributes.get(ALLOWED_MAPPERS_ATTRIBUTE));
        assertEquals("managed", attributes.get(UDS_ADDED_MAPPERS_ATTRIBUTE));
    }

    @Test
    void removesWithdrawnTypesWhilePreservingIndependentAllowances() throws Exception {
        // given
        stubAttributes();
        attributes.put(ALLOWED_MAPPERS_ATTRIBUTE, "manual,removed,retained");
        attributes.put(UDS_ADDED_MAPPERS_ATTRIBUTE, "removed,retained");

        // when
        ProtocolMapperDelegation.sync(session, realm, profiles("retained"));

        // then
        assertEquals("manual,retained", attributes.get(ALLOWED_MAPPERS_ATTRIBUTE));
        assertEquals("retained", attributes.get(UDS_ADDED_MAPPERS_ATTRIBUTE));
    }

    @Test
    void profileAttributeRemovalClearsOnlyTheUdsContribution() {
        // given
        stubAttributes();
        attributes.put(ALLOWED_MAPPERS_ATTRIBUTE, "managed,manual,shared");
        attributes.put(UDS_ADDED_MAPPERS_ATTRIBUTE, "managed");

        // when
        ProtocolMapperDelegation.sync(session, realm, null);

        // then
        assertEquals("manual,shared", attributes.get(ALLOWED_MAPPERS_ATTRIBUTE));
        assertEquals("", attributes.get(UDS_ADDED_MAPPERS_ATTRIBUTE));
    }

    @Test
    void removingLastExecutorClearsItsContribution() {
        // given
        stubAttributes();
        attributes.put(ALLOWED_MAPPERS_ATTRIBUTE, "managed");
        attributes.put(UDS_ADDED_MAPPERS_ATTRIBUTE, "managed");

        // when
        ProtocolMapperDelegation.sync(session, realm, "{\"profiles\":[{\"name\":\"UDS\",\"executors\":[]}]}");

        // then
        assertEquals("", attributes.get(ALLOWED_MAPPERS_ATTRIBUTE));
        assertEquals("", attributes.get(UDS_ADDED_MAPPERS_ATTRIBUTE));
    }

    @Test
    void unionsAllUdsProfilesAndHonorsDisabledDefaults() {
        // given
        stubAttributes();
        String profiles = """
                {"profiles":[
                  {"name":"first","executors":[{"executor":"uds-operator-permissions",
                    "configuration":{"use-default-allowed-protocol-mappers":false,
                      "allowed-protocol-mappers":["alpha","shared"]}}]},
                  {"name":"second","executors":[{"executor":"uds-operator-permissions",
                    "configuration":{"use-default-allowed-protocol-mappers":false,
                      "allowed-protocol-mappers-as-string":"beta, shared"}}]},
                  {"name":"unrelated","executors":[{"executor":"other",
                    "configuration":{"allowed-protocol-mappers":["ignored"]}}]}
                ]}
                """;

        // when
        ProtocolMapperDelegation.sync(session, realm, profiles);

        // then
        assertEquals("alpha,beta,shared", attributes.get(ALLOWED_MAPPERS_ATTRIBUTE));
        assertEquals("alpha,beta,shared", attributes.get(UDS_ADDED_MAPPERS_ATTRIBUTE));
        verifyNoInteractions(session);
    }

    @Test
    void doesNotWriteUnchangedAllowances() throws Exception {
        // given
        attributes.put(ALLOWED_MAPPERS_ATTRIBUTE, "alpha,manual");
        attributes.put(UDS_ADDED_MAPPERS_ATTRIBUTE, "alpha");
        when(realm.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));

        // when
        ProtocolMapperDelegation.sync(session, realm, profiles("alpha"));

        // then
        verify(realm, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void malformedProfilesFailBeforeAnyRealmWrite() {
        // given / when / then
        assertThrows(IllegalStateException.class,
                () -> ProtocolMapperDelegation.sync(session, realm, "{not-json"));
        verifyNoInteractions(realm);
    }

    private void stubAttributes() {
        when(realm.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(realm).setAttribute(anyString(), anyString());
    }

    private static String profiles(String... types) throws Exception {
        return JsonSerialization.writeValueAsString(Map.of("profiles", List.of(Map.of(
                "name", "UDS", "executors", List.of(Map.of(
                        "executor", UDSClientPolicyPermissionsExecutorFactory.PROVIDER_ID,
                        "configuration", Map.of("use-default-allowed-protocol-mappers", false,
                                "allowed-protocol-mappers", Arrays.asList(types))))))));
    }
}
