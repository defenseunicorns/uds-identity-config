package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.*;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.defenseunicorns.uds.keycloak.plugin.Common;
import com.defenseunicorns.uds.keycloak.plugin.X509Tools;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupFileMocks;
import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupX509Mocks;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ FileInputStream.class, File.class, X509Tools.class })
@PowerMockIgnore("javax.management.*")
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

    @Before
    public void setup() throws Exception {
        setupX509Mocks();
        setupFileMocks();

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

        // Verify that generateMattermostId method is called
        verify(userModel, times(1)).setSingleAttribute(eq(Common.USER_MATTERMOST_ID_ATTR), anyString());

        // Verify that the user has more than one attribute
        assert(userModel.getAttributes().size() > 1);

        // Verify that one of the attributes is "mattermostid"
        assert(userModel.getAttributes().containsKey(Common.USER_MATTERMOST_ID_ATTR));
    }

    @Test
    public void testOnEvent_Register_Failure() {
        // modify user to not have email
        userModel.setEmail(null);

        // Mock necessary method calls
        when(userProvider.getUserById(any(), any())).thenReturn(userModel);

        // Call the onEvent method with the mock Event
        eventListenerProvider.onEvent(event);

        // Verify that generateMattermostId method is called
        verify(userModel, times(1)).setSingleAttribute(eq(Common.USER_MATTERMOST_ID_ATTR), anyString());

        // Verify that one of the attributes is "mattermostid"
        assert(!userModel.getAttributes().containsKey(Common.USER_MATTERMOST_ID_ATTR));
    }

    @Test
    public void testOnEvent_Login() {
        // Change Mock Event to type LOGIN
        event.setType(EventType.LOGIN);

        // Call the onEvent method with the mock Event
        eventListenerProvider.onEvent(event);

        // Verify that generateMattermostId method is called
        verify(userModel, times(0)).setSingleAttribute(eq(Common.USER_MATTERMOST_ID_ATTR), anyString());

        // Verify user doesnt have attribute "mattermostid"
        assert(!userModel.getAttributes().containsKey(Common.USER_MATTERMOST_ID_ATTR));
    }
}
