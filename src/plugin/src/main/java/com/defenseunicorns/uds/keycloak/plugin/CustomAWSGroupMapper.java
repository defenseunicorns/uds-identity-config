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

public class CustomAWSGroupMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "aws-group-mapper";
    private static final String AWS_GROUPS_CLAIM = "aws-groups";

    @Override
    public String getDisplayCategory() {
        return "AWS Group Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Custom AWS Group Mapper";
    }

    @Override
    public String getHelpText() {
        return "Format groups for use with AWS authentication in this format; `group1:group2:group3`";
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
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession, KeycloakSession keycloakSession, ClientSessionContext clientSessionCtx) {
        Map<String, Object> otherClaims = token.getOtherClaims();
        if (otherClaims != null && otherClaims.containsKey(Common.GROUPS_CLAIM)) {
            Object groupsObj = otherClaims.get(Common.GROUPS_CLAIM);

            if (groupsObj instanceof List) {
                List<?> groupsList = (List<?>) groupsObj;

                // Only process if the list contains strings
                List<String> groups = groupsList.stream()
                        .filter(item -> item instanceof String)
                        .map(item -> (String) item)
                        .collect(Collectors.toList());

                // Transform group paths by joining with ":" or set empty if no valid groups
                String transformedGroups = groups.isEmpty() ? "" : groups.stream().collect(Collectors.joining(":"));

                // Add the transformed groups as a new claim "aws-groups"
                token.getOtherClaims().put(AWS_GROUPS_CLAIM, transformedGroups);
            } else {
                // If groups claim is not a list, set the aws-groups to empty
                token.getOtherClaims().put(AWS_GROUPS_CLAIM, "");
            }
        } else {
            // If no groups claim, set the aws-groups to empty
            token.getOtherClaims().put(AWS_GROUPS_CLAIM, "");
        }
    }
}
