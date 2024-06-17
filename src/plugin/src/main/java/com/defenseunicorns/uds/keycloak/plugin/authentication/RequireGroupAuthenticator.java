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
        String groupName = client.getAttribute("groups");
        String logPrefix = "UDS_GROUP_PROTECTION_AUTHENTICATE_" + authenticationSession.getParentSession().getId();

        if (user != null) {
            LOGGER.infof("%s user %s / %s", logPrefix, user.getId(), user.getUsername());
        } else {
            LOGGER.warnf("%s invalid user", logPrefix);

        }
        LOGGER.infof("%s client %s / %s", logPrefix, clientId, client.getName());

        // Check for a valid match
        if (groupName != null && groupName.length() > 0) {
            LOGGER.infof("%s %s client has group attribute %s", logPrefix, client.getName(), groupName);
            Optional<GroupModel> foundGroup = getGroupByName(groupName, realm);

            if (foundGroup.isPresent()) {
                checkIfUserIsAuthorized(context, realm, user, logPrefix, foundGroup.get());
            } else {
                LOGGER.warnf("%s Groups attribute (%s) failed to find matching group for %s client - the group does not exist in realm.", logPrefix, groupName, client.getName());
                // This failure (group not existing) is surfaced the same to the user as if the user is not a member of the group
                // Perhaps should be separate error
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

        // Must be a valid environment name
        if (group == null) {
            LOGGER.warnf("%s invalid group {}", logPrefix);
            context.failure(AuthenticationFlowError.CLIENT_DISABLED);
        } else {
            // Check if the user is a member of the specified group
            if (isMemberOfGroup(realm, user, group, logPrefix)) {
                LOGGER.infof("%s matched authorized group", logPrefix);
                success(context, user);
            } else {
                LOGGER.warnf("%s failed authorized group match", logPrefix);
                context.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
            }
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

        // No one likes null pointers
        if (realm == null || user == null || group == null) {
            LOGGER.warnf("%s realm, group or user null", logPrefix);
            return false;
        }

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
