package com.defenseunicorns.uds.keycloak.plugin.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Groups {
    @JsonProperty("anyOf")
    public String[] anyOf;
}
