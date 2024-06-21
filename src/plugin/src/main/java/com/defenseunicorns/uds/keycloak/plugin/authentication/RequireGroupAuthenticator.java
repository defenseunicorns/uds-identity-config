package com.defenseunicorns.uds.keycloak.plugin.authentication;

import java.util.stream.Collectors;
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
import org.keycloak.sessions.AuthenticationSessionModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simple {@link Authenticator} that checks of a user is member of a given {@link GroupModel Group}.
 */
public class RequireGroupAuthenticator implements Authenticator {

    /**
     * Logger variable.
     */
    private static final Logger LOGGER = Logger.getLogger(RequireGroupAuthenticator.class.getName());

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public void authenticate(final AuthenticationFlowContext context) {

        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        AuthenticationSessionModel authenticationSession = context.getAuthenticationSession();
        ClientModel client = authenticationSession.getClient();
        String clientId = client.getClientId();
        String logPrefix = "UDS_GROUP_PROTECTION_AUTHENTICATE_" + authenticationSession.getParentSession().getId();
    
        String groupsAttribute = client.getAttribute("uds.core.groups");
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode groupsNode = null;
    
        if (user != null) {
            LOGGER.infof("%s user %s / %s", logPrefix, user.getId(), user.getUsername());
        } else {
            LOGGER.warnf("%s invalid user", logPrefix);
            context.failure(AuthenticationFlowError.INVALID_USER);
        }
        LOGGER.infof("%s client %s / %s", logPrefix, clientId, client.getName());
    
        if (groupsAttribute != null && !groupsAttribute.isEmpty()) {
            try {
                groupsNode = objectMapper.readTree(groupsAttribute).path("anyOf");
            } catch (Exception e) {
                LOGGER.errorf("%s Failed to parse groups JSON: %s", logPrefix, e.getMessage());
                context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
                return;
            }
    
            if (groupsNode.isArray() && groupsNode.size() > 0) {
                LOGGER.infof("%s %s client has group attribute %s", logPrefix, client.getName(), groupsNode.toString());
                
                // limit the number of log statements from for loop
                boolean foundGroup = false;
    
                for (JsonNode groupNode : groupsNode) {
                    String groupName = groupNode.asText();
                    Optional<GroupModel> group = getGroupByName(groupName, realm);
    
                    if (group.isPresent()) {
                        checkIfUserIsAuthorized(context, realm, user, logPrefix, group.get());
                        foundGroup = true;
                        break;
                    }
                }
    
                if (!foundGroup) {
                    LOGGER.warnf("%s Groups attribute (%s) failed to find any matching group for %s client - the groups do not exist in realm.", logPrefix, groupsNode.toString(), client.getName());
                    context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
                }
            } else {
                LOGGER.warnf("%s Groups attribute for %s client does not contain a valid anyOf array", logPrefix, client.getName());
                context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
            }
        } else {
            LOGGER.infof("%s No groups detected for %s client", logPrefix, client.getName());
            success(context, user);
        }
    }

    private Optional<GroupModel> getGroupByName(final String groupName, final RealmModel realm) {
        return realm.getGroupsStream()
            .filter(group -> group.getName().equals(groupName))
            .findFirst();
    }

    private void checkIfUserIsAuthorized(
        final AuthenticationFlowContext context,
        final RealmModel realm,
        final UserModel user,
        final String logPrefix,
        final GroupModel group) {

        // Check if the user is a member of the specified group
        if (isMemberOfGroup(realm, user, group, logPrefix)) {
            LOGGER.infof("%s matched authorized group", logPrefix);
            success(context, user);
        } else {
            LOGGER.warnf("%s failed authorized group match", logPrefix);
            context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
        }
    }

    private void success(final AuthenticationFlowContext context, final UserModel user) {
        context.success();
    }

    private boolean isMemberOfGroup(
        final RealmModel realm,
        final UserModel user,
        final GroupModel group,
        final String logPrefix) {

        String groupList = user.getGroupsStream()
                .map(GroupModel::getId)
                .collect(Collectors.joining(","));

        LOGGER.infof("%s user groups %s", logPrefix, groupList);

        return user.isMemberOf(group);
    }

    @Override
    public void action(final AuthenticationFlowContext authenticationFlowContext) {
        // no implementation needed here
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public boolean requiresUser() {
        return false;
    }

    /**
     * This implementation is not intended to be overridden.
     */
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