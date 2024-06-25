package com.defenseunicorns.uds.keycloak.plugin.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Groups {
    @JsonProperty("anyOf")
    public String[] anyOf;

    public JsonNode path(String fieldName) {
        if (!"anyOf".equals(fieldName)) {
            throw new UnsupportedOperationException("Invalid field name: " + fieldName);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(anyOf);
    }
}
