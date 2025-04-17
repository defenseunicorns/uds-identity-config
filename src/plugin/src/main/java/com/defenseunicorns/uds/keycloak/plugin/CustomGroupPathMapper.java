/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;
import org.keycloak.models.ProtocolMapperModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomGroupPathMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper,
        OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "bare-group-path-mapper";
    public static final String GROUPS_CLAIM = "groups";
    public static final String BARE_GROUPS_CLAIM = "bare-groups";

    @Override
    public String getDisplayCategory() {
        return "Bare Group Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Custom Group Path Mapper";
    }

    @Override
    public String getHelpText() {
        return "Transforms group paths by removing leading slashes.";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return new ArrayList<>();
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
                            UserSessionModel userSession, KeycloakSession keycloakSession,
                            ClientSessionContext clientSessionCtx) {

        Map<String, Object> otherClaims = token.getOtherClaims();
        if (otherClaims != null && otherClaims.containsKey(GROUPS_CLAIM)) {
            Object groupsObj = otherClaims.get(GROUPS_CLAIM);

            if (groupsObj instanceof List) {
                List<?> groupsList = (List<?>) groupsObj;

                if (!groupsList.isEmpty() && groupsList.get(0) instanceof String) {
                    List<String> groups = groupsList.stream()
                            .filter(item -> item instanceof String)
                            .map(item -> (String) item)
                            .collect(Collectors.toList());

                    // Transform group paths by removing leading slashes
                    List<String> transformedGroups = groups.stream()
                            .map(group -> group.startsWith("/") ? group.substring(1) : group)
                            .collect(Collectors.toList());

                    // Add the transformed groups as a new claim "bare-groups"
                    token.getOtherClaims().put(BARE_GROUPS_CLAIM, transformedGroups);
                }
            }
        }
    }
}
