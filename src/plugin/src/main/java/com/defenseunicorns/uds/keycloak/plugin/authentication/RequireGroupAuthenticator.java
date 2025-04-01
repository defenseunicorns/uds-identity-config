/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication;

import java.util.Arrays;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.*;
import com.defenseunicorns.uds.keycloak.plugin.Common;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simple {@link Authenticator} that checks if a user is a member of a given {@link GroupModel Group}.
 */
public class RequireGroupAuthenticator implements Authenticator {

    private static final Logger LOGGER = Logger.getLogger(RequireGroupAuthenticator.class.getName());

    public static final String TAC_USER_ATTRIBUTE = "uds.tac.session.id";

    @Override
    public void authenticate(final AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            LOGGER.warn("Invalid user");
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        ClientModel client = context.getAuthenticationSession().getClient();

        // if client is in ignore list, automatic success and finish
        if(Common.GROUP_PROTECTION_IGNORE_CLIENTS.contains(client.getClientId())){
            context.success();
            return;
        }

        String groupsAttribute = client.getAttribute("uds.core.groups");

        if (groupsAttribute == null || groupsAttribute.isEmpty()) {
            LOGGER.infof("No groups detected for client %s", client.getName());
            success(context, user);
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
                    success(context, user);
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

    protected void success(final AuthenticationFlowContext context, final UserModel user) {
        boolean shouldAddTAC = true;

        if (isConditionalTACActive(context)) {
            String parentSessionId = context.getAuthenticationSession().getParentSession().getId();
            LOGGER.debugf("Conditional TAC, parent session ID: %s", parentSessionId);
            if (user.getAttributes().get(TAC_USER_ATTRIBUTE) != null) {
                String userSessionId = user.getAttributes().get(TAC_USER_ATTRIBUTE).get(0);
                LOGGER.debugf("Conditional TAC, user session ID: %s", parentSessionId);
                if (parentSessionId.equals(userSessionId)) {
                    shouldAddTAC = false;
                    LOGGER.info("User already accepted Terms and Conditions for this session. Skipping...");
                } else {
                    LOGGER.info("Stale session detected, asking user to accept Terms and Conditions again");
                }
            } else {
                LOGGER.info("User hasn't accepted Terms and Conditions. Requesting user accept them.");
            }
            user.setAttribute(TAC_USER_ATTRIBUTE, Arrays.asList(parentSessionId));
        } else {
            // Keycloak admins configured the plugin to ask for TAC every time
            shouldAddTAC = true;
        }

        if (shouldAddTAC) {
            user.addRequiredAction("TERMS_AND_CONDITIONS");
        }
        context.success();
    }

    protected boolean isConditionalTACActive(AuthenticationFlowContext context) {
        LOGGER.debug("Evaluating conditional TAC");
        AuthenticatorConfigModel authConfig = context.getAuthenticatorConfig();
        if (authConfig != null && authConfig.getConfig() != null) {
            String tocPerSession = authConfig.getConfig().get(RequireGroupAuthenticatorFactory.TOC_PER_SESSION_CONFIG_NAME);
            LOGGER.debugf("Custom Authentication Config is configured, TAC per session setting: %s", tocPerSession);
            return Boolean.valueOf(tocPerSession);
        } else {
            LOGGER.debug("No AuthenticatorConfig is configured, using default (true) value");
        }
        return true;
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
