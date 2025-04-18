/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.keycloak.representations.idm.ClientPolicyExecutorConfigurationRepresentation;

import java.util.List;

public class UDSClientPolicyPermissionsExecutorConfiguration extends ClientPolicyExecutorConfigurationRepresentation {

    @JsonProperty(UDSClientPolicyPermissionsExecutorFactory.ADDITIONAL_ALLOWED_PROTOCOL_MAPPER_TYPES)
    private List<String> allowedProtocolMappers;

    @JsonProperty(value = UDSClientPolicyPermissionsExecutorFactory.USE_DEFAULT_ALLOWED_PROTOCOL_MAPPER_TYPES, defaultValue = "true")
    private boolean useDefaultAllowedProtocolMappers = true;

    @JsonProperty(UDSClientPolicyPermissionsExecutorFactory.ADDITIONAL_ALLOWED_CLIENT_SCOPES)
    private List<String> allowedClientScopes;

    @JsonProperty(value = UDSClientPolicyPermissionsExecutorFactory.USE_DEFAULT_ALLOWED_CLIENT_SCOPES, defaultValue = "true")
    private boolean useDefaultAllowedClientScopes = true;

    public List<String> getAllowedProtocolMappers() {
        return allowedProtocolMappers;
    }

    public void setAllowedProtocolMappers(List<String> allowedProtocolMappers) {
        this.allowedProtocolMappers = allowedProtocolMappers;
    }

    public boolean isUseDefaultAllowedProtocolMappers() {
        return useDefaultAllowedProtocolMappers;
    }

    public void setUseDefaultAllowedProtocolMappers(boolean useDefaultAllowedProtocolMappers) {
        this.useDefaultAllowedProtocolMappers = useDefaultAllowedProtocolMappers;
    }

    public List<String> getAllowedClientScopes() {
        return allowedClientScopes;
    }

    public void setAllowedClientScopes(List<String> allowedClientScopes) {
        this.allowedClientScopes = allowedClientScopes;
    }

    public boolean isUseDefaultAllowedClientScopes() {
        return useDefaultAllowedClientScopes;
    }

    public void setUseDefaultAllowedClientScopes(boolean useDefaultAllowedClientScopes) {
        this.useDefaultAllowedClientScopes = useDefaultAllowedClientScopes;
    }

    @Override
    public String toString() {
        return "UDSClientPolicyPermissionsExecutorConfiguration{" +
                "allowedProtocolMappers=" + allowedProtocolMappers +
                ", useDefaultAllowedProtocolMappers=" + useDefaultAllowedProtocolMappers +
                ", allowedClientScopes=" + allowedClientScopes +
                ", useDefaultAllowedClientScopes=" + useDefaultAllowedClientScopes +
                '}';
    }
}