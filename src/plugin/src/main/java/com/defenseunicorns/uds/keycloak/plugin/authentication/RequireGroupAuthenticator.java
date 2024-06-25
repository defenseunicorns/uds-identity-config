package com.defenseunicorns.uds.keycloak.plugin.authentication;

import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.ClientModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simple {@link Authenticator} that checks if a user is a member of a given {@link GroupModel Group}.
 */
public class RequireGroupAuthenticator implements Authenticator {

    private static final Logger LOGGER = Logger.getLogger(RequireGroupAuthenticator.class.getName());

    @Override
    public void authenticate(final AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            logAndFail(context, "Invalid user", AuthenticationFlowError.INVALID_USER);
            return;
        }

        ClientModel client = context.getAuthenticationSession().getClient();
        String groupsAttribute = client.getAttribute("uds.core.groups");

        if (groupsAttribute == null || groupsAttribute.isEmpty()) {
            LOGGER.infof("No groups detected for client %s", client.getName());
            context.success();
            return;
        }

        JsonNode groupsNode = parseGroupsAttribute(groupsAttribute);
        if (groupsNode == null) {
            logAndFail(context, "Failed to parse groups JSON", AuthenticationFlowError.INVALID_CLIENT_SESSION);
            return;
        }

        handleGroupAuthentication(context, user, groupsNode);
    }

    private JsonNode parseGroupsAttribute(String groupsAttribute) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(groupsAttribute).path("anyOf");
        } catch (Exception e) {
            LOGGER.errorf("Failed to parse groups JSON: %s", e.getMessage());
            return null;
        }
    }

    private void handleGroupAuthentication(AuthenticationFlowContext context, UserModel user, JsonNode groupsNode) {
        if (!groupsNode.isArray() || groupsNode.size() == 0) {
            logAndFail(context, "Groups attribute does not contain a valid anyOf array", AuthenticationFlowError.INVALID_CLIENT_SESSION);
            return;
        }

        RealmModel realm = context.getRealm();
        boolean foundGroup = false;

        for (JsonNode groupNode : groupsNode) {
            String groupName = groupNode.asText();
            Optional<GroupModel> group = getGroupByPath(groupName, realm);

            if (group.isPresent()) {
                if (isMemberOfExactGroup(user, group.get())) {
                    LOGGER.infof("User %s is authorized for group %s", user.getUsername(), groupName);
                    context.success();
                    return;
                } else {
                    foundGroup = true;
                }
            }
        }

        if (foundGroup) {
            logAndFail(context, "User is not a member of the required group(s)", AuthenticationFlowError.INVALID_CLIENT_SESSION);
        } else {
            logAndFail(context, "Required group(s) do not exist in realm", AuthenticationFlowError.INVALID_CLIENT_SESSION);
        }
    }

    private Optional<GroupModel> getGroupByPath(final String groupPath, final RealmModel realm) {
        return realm.getGroupsStream()
            .filter(group -> buildGroupPath(group).equals(groupPath))
            .findFirst();
    }

    private String buildGroupPath(GroupModel group) {
        StringBuilder path = new StringBuilder();
        GroupModel currentGroup = group;
        while (currentGroup != null) {
            path.insert(0, "/" + currentGroup.getName());
            currentGroup = currentGroup.getParent();
        }
        return path.toString();
    }

    private boolean isMemberOfExactGroup(UserModel user, GroupModel group) {
        return user.getGroupsStream()
            .anyMatch(userGroup -> buildGroupPath(userGroup).equals(buildGroupPath(group)));
    }

    private void logAndFail(AuthenticationFlowContext context, String message, AuthenticationFlowError error) {
        LOGGER.warn(message);
        context.failure(error);
    }

    @Override
    public void action(final AuthenticationFlowContext authenticationFlowContext) {
        // no implementation needed here
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(
        final KeycloakSession keycloakSession,
        final RealmModel realmModel,
        final UserModel userModel) {

        return true;
    }

    @Override
    public void setRequiredActions(
        final KeycloakSession keycloakSession,
        final RealmModel realmModel,
        final UserModel userModel) {

        // no implementation needed here
    }

    @Override
    public void close() {
        // no implementation needed here
    }
}
