package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class JSONLogEventListenerProviderTest {

    @Mock
    private Logger logger;

    private JSONLogEventListenerProvider provider;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalSystemOut = System.out;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        provider = new JSONLogEventListenerProvider();
        System.setOut(new PrintStream(outputStream));
    }

    @After
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

        // Use reflection to call the private convertEventToJson method
        Method convertEventToJsonMethod = JSONLogEventListenerProvider.class.getDeclaredMethod("convertEventToJson", Event.class);
        convertEventToJsonMethod.setAccessible(true);
        ObjectMapper objectMapper = new ObjectMapper();
        String expectedJson = objectMapper.writeValueAsString(convertEventToJsonMethod.invoke(provider, event));

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

        // Use reflection to call the private convertAdminEventToJson method
        Method convertAdminEventToJsonMethod = JSONLogEventListenerProvider.class.getDeclaredMethod("convertAdminEventToJson", AdminEvent.class);
        convertAdminEventToJsonMethod.setAccessible(true);
        ObjectMapper objectMapper = new ObjectMapper();
        String expectedJson = objectMapper.writeValueAsString(convertAdminEventToJsonMethod.invoke(provider, adminEvent));

        provider.onEvent(adminEvent, true);

        String actualJson = outputStream.toString().trim();
        assertEquals(expectedJson, actualJson);
    }
}
