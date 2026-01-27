/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.defenseunicorns.uds.keycloak.plugin.Common;

import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
public class RegisterEventListenerTest {

    @Mock
    KeycloakSession keycloakSession;
    @Mock
    RealmModel realmModel;
    @Mock
    RealmProvider realmProvider;
    @Mock
    UserModel userModel;
    @Mock
    UserProvider userProvider;

    Event event;
    String realmId;
    String userId;
    RegisterEventListenerProvider eventListenerProvider;

    @BeforeEach
    public void setup() throws Exception {
        // Mock necessary method calls
        when(keycloakSession.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm(any())).thenReturn(realmModel);
        when(keycloakSession.users()).thenReturn(userProvider);
        when(userProvider.getUserById(any(), any())).thenReturn(userModel);
        when(userModel.getId()).thenReturn(UUID.randomUUID().toString());
        when(userModel.getEmail()).thenReturn("test@example.com");

        // Create a mock Event with type REGISTER
        event = new Event();
        event.setType(EventType.REGISTER);
        realmId = UUID.randomUUID().toString();
        event.setRealmId(realmId);
        userId = UUID.randomUUID().toString();
        event.setUserId(userId);

        // Create instance of CustomEventListenerProvider
        eventListenerProvider = new RegisterEventListenerProvider(keycloakSession);
    }

    @Test
    public void testOnEvent_Register_Success() {
        // Call the onEvent method with the mock Event
        eventListenerProvider.onEvent(event);

        // Verify that generateMattermostId method is called (setSingleAttribute is called with mattermostid)
        verify(userModel, times(1)).setSingleAttribute(eq(Common.USER_MATTERMOST_ID_ATTR), anyString());
    }

    @Test
    public void testOnEvent_Register_Failure() {
        // modify user to not have email
        when(userModel.getEmail()).thenReturn(null);

        // Call the onEvent method with the mock Event
        eventListenerProvider.onEvent(event);

        // Verify that setSingleAttribute is NOT called when email is null
        verify(userModel, times(0)).setSingleAttribute(eq(Common.USER_MATTERMOST_ID_ATTR), anyString());
    }

    @Test
    public void testOnEvent_Login() {
        // Change Mock Event to type LOGIN
        event.setType(EventType.LOGIN);

        // Call the onEvent method with the mock Event
        eventListenerProvider.onEvent(event);

        // Verify that setSingleAttribute is NOT called for LOGIN events
        verify(userModel, times(0)).setSingleAttribute(eq(Common.USER_MATTERMOST_ID_ATTR), anyString());
    }
}
