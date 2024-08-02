package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map.Entry;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JSONLogEventListenerProvider implements EventListenerProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onEvent(Event event) {
        JsonNode jsonMessage = convertUserEvent(event);
        System.out.println(jsonMessage);
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        JsonNode jsonMessage = convertAdminEvent(adminEvent);
        System.out.println(jsonMessage);
    }

    @Override
    public void close() {
        // Clean up resources if needed
    }

    private JsonNode convertAdminEvent(AdminEvent adminEvent) {
        return convertEventToJson(objectMapper.valueToTree(adminEvent), "ADMIN");
    }

    private JsonNode convertUserEvent(Event event) {
        return convertEventToJson(objectMapper.valueToTree(event), "USER");
    }

    private JsonNode convertEventToJson(JsonNode originalJsonMessage, String eventType) {
        // Extract the "time" value
        long timeMillis = originalJsonMessage.has("time") ? originalJsonMessage.get("time").asLong() : 0;

        // Convert time to ISO 8601 format with nanosecond precision
        Instant instant = Instant.ofEpochMilli(timeMillis);
        String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSX")
                .withZone(ZoneOffset.UTC)
                .format(instant);

        // Create a new ObjectNode for ordered fields
        ObjectNode jsonMessage = objectMapper.createObjectNode();

        // Add fields in the desired order
        jsonMessage.put("timestamp", formattedTime);
        jsonMessage.put("loggerName", "uds.keycloak.plugin.eventListeners.JSONLogEventListenerProvider");
        jsonMessage.put("eventType", eventType);

        Iterator<Entry<String, JsonNode>> fields = originalJsonMessage.fields();
        while (fields.hasNext()) {
            Entry<String, JsonNode> field = fields.next();
            jsonMessage.set(field.getKey(), field.getValue());
        }

        return jsonMessage;
    }

}
