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
        when(authenticationSession.getParentSession()).thenReturn(parentAuthenticationSession);
        when(parentAuthenticationSession.getId()).thenReturn("test-session-id");

        when(group.getName()).thenReturn("httpbin");
        when(realm.getGroupsStream()).thenReturn(Stream.of(group));
    }

    @Test
    public void testShouldRejectUnknownGroups() throws Exception {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"unknown-group\"]}");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectNonGroupMembers() throws Exception {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"httpbin\"]}");
        when(group.getName()).thenReturn("httpbin");
        when(user.isMemberOf(group)).thenReturn(false); // User is not a member of the group

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldAcceptEmptyGroups() {
        when(client.getAttribute("uds.core.groups")).thenReturn(""); // Empty groups attribute

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    public void testShouldAcceptUserInGroup() throws Exception {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [\"httpbin\"]}");
        when(group.getName()).thenReturn("httpbin");
        when(user.isMemberOf(group)).thenReturn(true); // User is a member of the group

        authenticator.authenticate(context);

        verify(context).success();
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
    public void testShouldRejectMemberGroupNull() {
        when(client.getAttribute("uds.core.groups")).thenReturn("{\"anyOf\": [null]}"); // JSON with null group

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }
}
