/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RequireGroupAuthenticatorTest {

    @InjectMocks
    @Spy
    private RequireGroupAuthenticator authenticator;

    @Mock
    private AuthenticationFlowContext context;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private GroupModel group;

    @Mock
    private GroupModel parentGroup;

    @Mock
    private AuthenticationSessionModel authenticationSession;

    @Mock
    private RootAuthenticationSessionModel parentAuthenticationSession;

    @Mock
    private ClientModel client;

    @Mock
    private GroupProvider groupProvider;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(parentAuthenticationSession.getId()).thenReturn("test-id");

        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticationSession()).thenReturn(authenticationSession);
        when(authenticationSession.getClient()).thenReturn(client);
        when(authenticationSession.getParentSession()).thenReturn(parentAuthenticationSession);

        when(group.getName()).thenReturn("Admin");
        when(group.getParent()).thenReturn(parentGroup);
        when(parentGroup.getName()).thenReturn("UDS Core");

        when(realm.getGroupsStream()).thenReturn(Stream.of(group, parentGroup));
    }

    @Test
    public void testShouldRejectUnknownGroups() throws Exception {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/unknown-group\"]}");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectNonGroupMembers() throws Exception {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\"]}");
        when(group.getName()).thenReturn("Admin");
        when(user.getGroupsStream()).thenReturn(Stream.of()); // User is not a member of any group

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldAcceptEmptyGroupsAttribute() {
        when(client.getAttribute("uds.core.groups")).thenReturn(""); // Empty groups attribute

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    public void testShouldAcceptUserInGroup() throws Exception {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(group)); // User is a member of the group

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    public void testShouldAcceptUserInParentGroup() throws Exception {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(parentGroup)); // User is a member of the parent group

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    public void testShouldRejectUserNotInParentGroup() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(group)); // User is a member of the admin group

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectNullUser() {
        when(context.getUser()).thenReturn(null); // Simulating null user

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_USER);
    }

    @Test
    public void testShouldRejectFailedJsonParse() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [invalid-json]}"); // Invalid JSON

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectNoGroupsArray() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{}"); // JSON without 'anyOf' array

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectNullGroupInAnyOf() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [null]}"); // JSON with null group

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectUserInDifferentGroup() throws Exception {
        GroupModel differentGroup = mock(GroupModel.class);
        when(differentGroup.getName()).thenReturn("DifferentGroup");
        when(differentGroup.getParent()).thenReturn(null);

        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(differentGroup)); // User is a member of a different group

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testShouldThrowExceptionForInvalidGroupNameWithSlash() {
        GroupModel invalidGroup = mock(GroupModel.class);
        when(invalidGroup.getName()).thenReturn("Invalid/Group");

        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"Invalid/Group\"]}");
        when(realm.getGroupsStream()).thenReturn(Stream.of(invalidGroup));

        authenticator.authenticate(context);
    }

    @Test
    public void testShouldNotThrowExceptionForValidGroupNameWithEscapedSlash() {
        GroupModel validGroup = mock(GroupModel.class);
        when(validGroup.getName()).thenReturn("Invalid~/Group");

        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"Invalid/Group\"]}");
        when(realm.getGroupsStream()).thenReturn(Stream.of(validGroup));

        authenticator.authenticate(context);
    }

    @Test
    public void testShouldAcceptClientInIgnoreList() {
        when(client.getClientId()).thenReturn("account-console");

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    public void testConditionalTACWhenThePluginIsNotConfigured() {
        when(context.getAuthenticatorConfig()).thenReturn(null);

        boolean result = authenticator.isConditionalTACActive(context);

        assertTrue(result);
    }

    @Test
    public void testConditionalTACWhenPluginConfigurationIsDisabled() {
        AuthenticatorConfigModel authConfig = mock(AuthenticatorConfigModel.class);
        when(context.getAuthenticatorConfig()).thenReturn(authConfig);
        when(authConfig.getConfig()).thenReturn(Collections.singletonMap(RequireGroupAuthenticatorFactory.TOC_PER_SESSION_CONFIG_NAME, "false"));

        boolean result = authenticator.isConditionalTACActive(context);

        assertFalse(result);
    }

    @Test
    public void testConditionalTACWhenPluginConfigurationIsEnabled() {
        AuthenticatorConfigModel authConfig = mock(AuthenticatorConfigModel.class);
        when(context.getAuthenticatorConfig()).thenReturn(authConfig);
        when(authConfig.getConfig()).thenReturn(Collections.singletonMap(RequireGroupAuthenticatorFactory.TOC_PER_SESSION_CONFIG_NAME, "true"));

        boolean result = authenticator.isConditionalTACActive(context);

        assertTrue(result);
    }

    @Test
    public void testAddingTACWhenSessionIDsMismatch() {
        doReturn(true).when(authenticator).isConditionalTACActive(context);

        when(context.getAuthenticationSession().getParentSession().getId()).thenReturn("test-session-id");
        when(user.getAttributes()).thenReturn(Collections.singletonMap(RequireGroupAuthenticator.TAC_USER_ATTRIBUTE, Collections.singletonList("doesn't match")));

        authenticator.success(context, user);

        verify(user).addRequiredAction("TERMS_AND_CONDITIONS");
        verify(context).success();
    }

    @Test
    public void testNotAddingTACWhenSessionIDsMatch() {
        doReturn(true).when(authenticator).isConditionalTACActive(context);

        when(context.getAuthenticationSession().getParentSession().getId()).thenReturn("test-session-id");
        when(user.getAttributes()).thenReturn(Collections.singletonMap(RequireGroupAuthenticator.TAC_USER_ATTRIBUTE, Collections.singletonList("test-session-id")));

        authenticator.success(context, user);

        verify(user, never()).addRequiredAction("TERMS_AND_CONDITIONS");
        verify(context).success();
    }

    @Test
    public void testAddingTACWhenUserHasNoAttribute() {
        doReturn(true).when(authenticator).isConditionalTACActive(context);

        when(context.getAuthenticationSession().getParentSession().getId()).thenReturn("test-session-id");
        when(user.getAttributes()).thenReturn(Collections.emptyMap());

        authenticator.success(context, user);

        verify(user).addRequiredAction("TERMS_AND_CONDITIONS");
        verify(context).success();
    }

    @Test
    public void testAddingTACWhenConditionalTACIsDisabled() {
        doReturn(false).when(authenticator).isConditionalTACActive(context);

        authenticator.success(context, user);

        verify(user).addRequiredAction("TERMS_AND_CONDITIONS");
        verify(context).success();
    }
}
