package com.defenseunicorns.uds.keycloak.plugin.authentication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.models.ClientModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RequireGroupAuthenticatorTest {

    @InjectMocks
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

        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticationSession()).thenReturn(authenticationSession);
        when(authenticationSession.getClient()).thenReturn(client);

        when(group.getName()).thenReturn("Admin");
        when(group.getParent()).thenReturn(parentGroup);
        when(parentGroup.getName()).thenReturn("UDS Core");

        when(realm.getGroupsStream()).thenReturn(Stream.of(group, parentGroup));
    }

    @Test
    public void testShouldRejectUnknownGroups() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/unknown-group\"]}");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectNonGroupMembers() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\"]}");
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
    public void testShouldAcceptUserInGroup() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(group)); // User is a member of the group

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    public void testShouldAcceptUserInParentGroup() {
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
    public void testShouldRejectUserInDifferentGroup() {
        GroupModel differentGroup = mock(GroupModel.class);
        when(differentGroup.getName()).thenReturn("DifferentGroup");
        when(differentGroup.getParent()).thenReturn(null);

        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(differentGroup)); // User is a member of a different group

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectInvalidJsonStructure() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"invalidKey\": [\"/UDS Core/Admin\"]}");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectEmptyAnyOfArray() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": []}");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldAcceptUserWithMultipleGroups() {
        GroupModel anotherGroup = mock(GroupModel.class);

        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\", \"/AnotherGroup\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(group, anotherGroup)); // User is a member of multiple groups

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    public void testShouldRejectMalformedGroupPaths() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"//UDS Core//Admin\"]}");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectUserInSubgroup() {
        GroupModel subgroup = mock(GroupModel.class);
        when(subgroup.getName()).thenReturn("Subgroup");
        when(subgroup.getParent()).thenReturn(group);
        when(group.getName()).thenReturn("Admin");
        when(group.getParent()).thenReturn(parentGroup);

        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/UDS Core/Admin\"]}");
        when(user.getGroupsStream()).thenReturn(Stream.of(subgroup)); // User is a member of a subgroup

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectUserWithCaseSensitiveGroupNames() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"/uds core/admin\"]}"); // Different case

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }
}
