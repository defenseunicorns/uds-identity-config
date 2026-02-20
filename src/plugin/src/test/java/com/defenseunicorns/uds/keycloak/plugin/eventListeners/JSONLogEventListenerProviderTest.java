/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
public class JSONLogEventListenerProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmProvider realmProvider;

    @Mock
    private UserProvider userProvider;

    @Mock
    private RealmModel realmModel;

    @Mock
    private UserModel userModel;

    @Mock
    private GroupModel groupModel;

    @Mock
    private GroupModel parentGroupModel;

    @InjectMocks
    private JSONLogEventListenerProvider provider;

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalSystemOut = System.out;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        System.setOut(new PrintStream(outputStream));
        // common session mocks used by addGroups()
        when(session.realms()).thenReturn(realmProvider);
        when(session.users()).thenReturn(userProvider);
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalSystemOut);
    }

    @Test
    public void testOnEvent() throws Exception {
        Event event = new Event();
        // Populate event object with test data
        event.setClientId("test-client");
        event.setType(EventType.LOGIN);
        event.setUserId("test-user");

        // Use reflection to call the private convertUserEvent method
        Method convertUserEventMethod = JSONLogEventListenerProvider.class.getDeclaredMethod("convertUserEvent", Event.class);
        convertUserEventMethod.setAccessible(true);
        String expectedJson = objectMapper.writeValueAsString(convertUserEventMethod.invoke(provider, event));

        provider.onEvent(event);

        String actualJson = outputStream.toString().trim();
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testOnAdminEvent() throws Exception {
        AdminEvent adminEvent = new AdminEvent();
        // Populate adminEvent object with test data
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setAuthDetails(new AuthDetails());

        // Use reflection to call the private convertAdminEvent method
        Method convertAdminEventMethod = JSONLogEventListenerProvider.class.getDeclaredMethod("convertAdminEvent", AdminEvent.class);
        convertAdminEventMethod.setAccessible(true);
        String expectedJson = objectMapper.writeValueAsString(convertAdminEventMethod.invoke(provider, adminEvent));

        provider.onEvent(adminEvent, true);

        String actualJson = outputStream.toString().trim();
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testAddingGroupsWithUDSCoreAdmin() throws Exception {
        // Given
        String realmId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setAuthDetails(new AuthDetails());
        adminEvent.setRealmId(realmId);
        adminEvent.setRepresentation("{\"id\":\"" + userId + "\"}");

        when(realmProvider.getRealm(eq(realmId))).thenReturn(realmModel);
        when(userProvider.getUserById(eq(realmModel), eq(userId))).thenReturn(userModel);

        when(userModel.getGroupsStream()).thenReturn(Stream.of(groupModel));
        when(groupModel.getName()).thenReturn("Admin");
        when(groupModel.getParent()).thenReturn(parentGroupModel);
        when(parentGroupModel.getName()).thenReturn("UDS Core");
        when(parentGroupModel.getParent()).thenReturn(null);

        // When
        provider.onEvent(adminEvent, true);
        String actualJson = outputStream.toString().trim();

        // Then
        JsonNode parsed = objectMapper.readTree(actualJson);
        assertTrue(parsed.has("representation"));
        JsonNode rep = objectMapper.readTree(parsed.get("representation").asText());
        assertTrue(rep.has("groups"));
        assertEquals("/UDS Core/Admin", rep.get("groups").get(0).asText());
    }

    @Test
    public void testAddingGroupsWithNoGroups() throws Exception {
        // Given
        String realmId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setAuthDetails(new AuthDetails());
        adminEvent.setRealmId(realmId);
        adminEvent.setRepresentation("{\"id\":\"" + userId + "\"}");

        when(realmProvider.getRealm(eq(realmId))).thenReturn(realmModel);
        when(userProvider.getUserById(eq(realmModel), eq(userId))).thenReturn(userModel);

        when(userModel.getGroupsStream()).thenReturn(Stream.empty());

        // When
        provider.onEvent(adminEvent, true);
        String actualJson = outputStream.toString().trim();

        // Then
        JsonNode parsed = objectMapper.readTree(actualJson);
        JsonNode rep = objectMapper.readTree(parsed.get("representation").asText());
        assertTrue(rep.has("groups"));
        assertEquals(0, rep.get("groups").size());
    }
}
