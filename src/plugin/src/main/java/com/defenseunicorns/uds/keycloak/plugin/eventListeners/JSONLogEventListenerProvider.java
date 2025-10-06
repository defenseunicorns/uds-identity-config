/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONLogEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final KeycloakSession session;

    public JSONLogEventListenerProvider(KeycloakSession session) {
        this.session = session;
    }

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
        addGroups(adminEvent);
        return convertEventToJson(objectMapper.valueToTree(adminEvent), "ADMIN");
    }

    private void addGroups(AdminEvent adminEvent) {
        if (adminEvent.getResourceType() == ResourceType.USER) {
            try {
                Map<String, Object> representationAsMap = objectMapper.readerFor(Map.class).readValue(adminEvent.getRepresentation());
                var userIdAsObject = representationAsMap.get("id");
                if (userIdAsObject != null) {
                    logger.debug("UserId: {}", userIdAsObject);
                    String userId = userIdAsObject.toString();
                    RealmModel realm = session.realms().getRealm(adminEvent.getRealmId());
                    UserModel user = session.users().getUserById(realm, userId);
                    List<String> groups = user.getGroupsStream()
                            .map(ModelToRepresentation::buildGroupPath)
                            .collect(Collectors.toCollection(ArrayList::new));
                    logger.debug("groups: {}", groups);
                    representationAsMap.putIfAbsent("groups", groups);
                    adminEvent.setRepresentation(objectMapper.writeValueAsString(representationAsMap));
                }
            } catch (Exception e) {
                logger.warn("Failed to append Group to the User. Grafana alerts might not be fully accurate. Skipping...", e);
            }
        }
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
