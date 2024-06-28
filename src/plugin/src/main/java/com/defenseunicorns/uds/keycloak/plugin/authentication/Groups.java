package com.defenseunicorns.uds.keycloak.plugin.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Groups {
    @JsonProperty("anyOf")
    public String[] anyOf;
}
