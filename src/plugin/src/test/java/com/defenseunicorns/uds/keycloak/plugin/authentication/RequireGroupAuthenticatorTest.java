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
import org.keycloak.models.KeycloakSession;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupFileMocks;


@RunWith(PowerMockRunner.class)
@PrepareForTest({ FileInputStream.class, File.class })
@PowerMockIgnore("javax.management.*")
public class RequireGroupAuthenticatorTest {

    private RequireGroupAuthenticator subject;
    private AuthenticationFlowContext context;
    private RealmModel realm;
    private UserModel user;
    private GroupModel group;
    private KeycloakSession session;
    private AuthenticationSessionModel authenticationSession;
    private RootAuthenticationSessionModel parentAuthenticationSession;
    private ClientModel client;
    private GroupProvider groupProvider;

    @Before
    public void setup() throws Exception {

        setupFileMocks();

        subject = new RequireGroupAuthenticator();

        context = mock(AuthenticationFlowContext.class);
        realm = mock(RealmModel.class);
        user = mock(UserModel.class);
        group = mock(GroupModel.class);
        session = mock(KeycloakSession.class);
        authenticationSession = mock(AuthenticationSessionModel.class);
        parentAuthenticationSession = mock(RootAuthenticationSessionModel.class);
        client = mock(ClientModel.class);
        groupProvider = mock(GroupProvider.class);

        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(context.getSession()).thenReturn(session);
        when(context.getAuthenticationSession()).thenReturn(authenticationSession);
        when(authenticationSession.getClient()).thenReturn(client);
        when(authenticationSession.getParentSession()).thenReturn(parentAuthenticationSession);
        when(parentAuthenticationSession.getId()).thenReturn("bleh");
        when(realm.getGroupById(anyString())).thenReturn(group);
        when(session.groups()).thenReturn(groupProvider);

    }

    @Test
    public void testShouldRejectUnknownGroups() {
        when(client.getClientId()).thenReturn("random-bad-client");
        when(client.getAttribute("groups")).thenReturn("httpbin");
        subject.authenticate(context);
        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldRejectNonGroupMembers() {
        List<GroupModel> groupList = new ArrayList<>(); 
        group.setName("httpbin");
        groupList.add(group);

        when(client.getClientId()).thenReturn("random-bad-client");
        when(client.getAttribute("groups")).thenReturn("httpbin");
        when(group.getName()).thenReturn("httpbin");
        subject.authenticate(context);
        verify(context).failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
    }

    @Test
    public void testShouldAcceptEmptyGroups() {
        when(client.getClientId()).thenReturn("random-bad-client");
        when(client.getAttribute("groups")).thenReturn("");
        subject.authenticate(context);
        verify(context).success();
    }
}
