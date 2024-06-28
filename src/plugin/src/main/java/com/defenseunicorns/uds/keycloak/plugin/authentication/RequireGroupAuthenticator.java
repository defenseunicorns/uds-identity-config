package com.defenseunicorns.uds.keycloak.plugin.authentication;


import java.util.Arrays;
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
            LOGGER.warn("Invalid user");
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        ClientModel client = context.getAuthenticationSession().getClient();
        String groupsAttribute = client.getAttribute("uds.core.groups");

        if (groupsAttribute == null || groupsAttribute.isEmpty()) {
            LOGGER.infof("No groups detected for client %s", client.getName());
            context.success();
            return;
        }

        Groups groups = parseGroupsAttribute(groupsAttribute);
        if (groups == null) {
            LOGGER.warn("Failed to parse groups JSON");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
            return;
        }

        RealmModel realm = context.getRealm();
        boolean foundGroup = false;

        for (String groupName : groups.anyOf) {
            Optional<GroupModel> group = getGroupByPath(groupName, realm);

            if (group.isPresent()) {
                if (isMemberOfExactGroup(user, group.get())) {
                    LOGGER.infof("User %s is authorized for group %s", user.getUsername(), groupName);
                    context.success();
                    return;
                }
                foundGroup = true;
            }
        }

        if (foundGroup) {
            LOGGER.warn("User is not a member of the required group(s)");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
            return;
        }

        LOGGER.warnf("Required group(s) %s do not exist in realm", Arrays.toString(groups.anyOf));
        context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    private Groups parseGroupsAttribute(String groupsAttribute) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Groups groups = objectMapper.readValue(groupsAttribute, Groups.class);
            if (groups.anyOf.length == 0) {
                LOGGER.errorf("Groups attribute does not contain a valid anyOf array");
                return null;
            }
            return groups;
        } catch (Exception e) {
            LOGGER.errorf("Failed to parse groups JSON: %s", e.getMessage());
            return null;
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
            String groupName = currentGroup.getName();
            //  disallow slashes in group names to protect against Keycloak allowing unescaped slashes in group names until https://github.com/defenseunicorns/uds-identity-config/issues/118
            if (groupName.contains("/")) {
                LOGGER.errorf("Group name '%s' contains a slash", groupName);
                throw new IllegalArgumentException("Group names cannot contain slashes");
            }
            path.insert(0, "/" + groupName);
            currentGroup = currentGroup.getParent();
        }
        return path.toString();
    }

    private boolean isMemberOfExactGroup(UserModel user, GroupModel group) {
        return user.getGroupsStream()
            .anyMatch(userGroup -> buildGroupPath(userGroup).equals(buildGroupPath(group)));
    }

    @Override
    public void action(final AuthenticationFlowContext authenticationFlowContext) {
        // no implementation needed here
    }

    @Override
    public boolean requiresUser() {
        return true;
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
