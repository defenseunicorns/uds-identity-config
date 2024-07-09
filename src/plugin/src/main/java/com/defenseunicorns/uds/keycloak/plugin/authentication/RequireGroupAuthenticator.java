package com.defenseunicorns.uds.keycloak.plugin.authentication;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
            LOGGER.warn("Failed to parse groups JSON or no valid groups defined");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
            return;
        }

        RealmModel realm = context.getRealm();
        List<GroupModel> realmGroups = realm.getGroupsStream().collect(Collectors.toList());
        List<GroupModel> userGroups = user.getGroupsStream().collect(Collectors.toList());

        if (!isMemberOfAnyOfGroups(userGroups, realmGroups, groups.anyOf) || !isMemberOfAllOfGroups(userGroups, realmGroups, groups.allOf)) {
            context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
            return;
        }

        LOGGER.infof("User %s is authorized", user.getUsername());
        context.success();
    }

    private Groups parseGroupsAttribute(String groupsAttribute) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Groups groups = objectMapper.readValue(groupsAttribute, Groups.class);
            if ((groups.anyOf == null || groups.anyOf.length == 0) && 
                (groups.allOf == null || groups.allOf.length == 0)) {
                LOGGER.errorf("Groups attribute does not contain a valid anyOf or allOf array");
                return null;
            }
            return groups;
        } catch (Exception e) {
            LOGGER.errorf("Failed to parse groups JSON: %s", e.getMessage());
            return null;
        }
    }

    private boolean isMemberOfAnyOfGroups(List<GroupModel> userGroups, List<GroupModel> realmGroups, String[] anyOfGroups) {
        if (anyOfGroups != null) {
            for (String groupName : anyOfGroups) {
                Optional<GroupModel> group = getGroupByPath(groupName, realmGroups);

                if (group.isPresent() && isMemberOfExactGroup(userGroups, group.get())) {
                    return true;
                }
            }
            LOGGER.warn("User is not a member of any required anyOf groups");
            return false;
        }
        return true;
    }

    private boolean isMemberOfAllOfGroups(List<GroupModel> userGroups, List<GroupModel> realmGroups, String[] allOfGroups) {
        if (allOfGroups != null) {
            for (String groupName : allOfGroups) {
                Optional<GroupModel> group = getGroupByPath(groupName, realmGroups);

                if (group.isEmpty() || !isMemberOfExactGroup(userGroups, group.get())) {
                    LOGGER.warn("User is not a member of all required allOf groups");
                    return false;
                }
            }
        }
        return true;
    }

    private Optional<GroupModel> getGroupByPath(final String groupPath, final List<GroupModel> allGroups) {
        return allGroups.stream()
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

    private boolean isMemberOfExactGroup(List<GroupModel> userGroupsList, GroupModel group) {
        return userGroupsList.stream()
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
