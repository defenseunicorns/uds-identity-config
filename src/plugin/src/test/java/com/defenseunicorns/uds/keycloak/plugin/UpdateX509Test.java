/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.utils.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.authenticators.x509.X509AuthenticatorConfigModel;
import org.keycloak.authentication.authenticators.x509.X509ClientCertificateAuthenticator;
import org.keycloak.common.crypto.UserIdentityExtractor;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.services.x509.X509ClientCertificateLookup;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class UpdateX509Test {

    @Mock
    KeycloakSession keycloakSession;
    @Mock
    KeycloakContext keycloakContext;
    @Mock
    AuthenticationSessionModel authenticationSessionModel;
    @Mock
    RootAuthenticationSessionModel rootAuthenticationSessionModel;
    @Mock
    HttpRequest httpRequest;
    @Mock
    RealmModel realmModel;
    @Mock
    X509ClientCertificateLookup x509ClientCertificateLookup;
    @Mock
    AuthenticatorConfigModel authenticatorConfigModel;
    @Mock
    X509ClientCertificateAuthenticator x509ClientCertificateAuthenticator;
    @Mock
    UserIdentityExtractor userIdentityExtractor;
    @Mock
    UserProvider userProvider;
    @Mock
    UserModel userModel;
    @Mock
    RequiredActionContext requiredActionContext;
    @Mock
    LoginFormsProvider loginFormsProvider;
    @Mock
    Config.Scope scope;

    public UpdateX509Test() {}

    @BeforeEach
    public void setupMockBehavior() throws Exception {
        // common mock implementations
        when(requiredActionContext.getSession()).thenReturn(keycloakSession);
        when(keycloakSession.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
        when(authenticationSessionModel.getParentSession()).thenReturn(rootAuthenticationSessionModel);
        when(rootAuthenticationSessionModel.getId()).thenReturn("xxx");
        when(requiredActionContext.getHttpRequest()).thenReturn(httpRequest);
        when(requiredActionContext.getRealm()).thenReturn(realmModel);

        // setup X509Tools
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        // create cert array and add the cert
        X509Certificate[] certList = new X509Certificate[1];
        X509Certificate x509Certificate2 = Utils.buildTestCertificate();
        certList[0] = x509Certificate2;
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certList);

        when(realmModel.getAuthenticatorConfigsStream()).thenAnswer((stream) -> {
            return Stream.of(authenticatorConfigModel);
        });

        // create map
        Map<String, String> mapSting = new HashMap<>();
        mapSting.put("x509-cert-auth.mapper-selection.user-attribute-name", "test");
        when(authenticatorConfigModel.getConfig()).thenReturn(mapSting);

        when(x509ClientCertificateAuthenticator
                .getUserIdentityExtractor(any(X509AuthenticatorConfigModel.class))).thenReturn(userIdentityExtractor);
        when(keycloakSession.users()).thenReturn(userProvider);
        when(userProvider.searchForUserByUserAttributeStream(any(RealmModel.class), anyString(), anyString()))
                .thenAnswer((stream) -> {
                    return Stream.of(userModel);
                });
    }

    @Test
    public void testEvaluateTriggersCondition1() throws Exception {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            x509ToolsMock.when(() -> X509Tools.getX509Username(eq(requiredActionContext))).thenReturn("something");

            when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
            when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn("authNote");

            // create cert array and add the cert
            X509Certificate[] certList = new X509Certificate[1];
            X509Certificate x509Certificate2 = Utils.buildTestCertificate();
            certList[0] = x509Certificate2;

            // create map - some value
            Map<String, List<String>> mapSting = new HashMap<>();
            List<String> listString = new ArrayList<>();
            listString.add("some value");
            mapSting.put("usercertificateIdentity", listString);

            when(requiredActionContext.getHttpRequest().getClientCertificateChain())
                .thenReturn(certList);
            when(requiredActionContext.getUser()).thenReturn(userModel);
            when(userModel.getAttributes()).thenReturn(mapSting);

            UpdateX509 updateX509 = new UpdateX509();
            updateX509.evaluateTriggers(requiredActionContext);

            // create map - empty value
            mapSting = new HashMap<>();
            mapSting.put("usercertificateIdentity", new ArrayList<>());

            when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn(null);
            when(userModel.getAttributes()).thenReturn(mapSting);

            updateX509 = new UpdateX509();
            updateX509.evaluateTriggers(requiredActionContext);

            // create map - null value
            mapSting = new HashMap<>();
            mapSting.put("usercertificateIdentity", null);

            when(userModel.getAttributes()).thenReturn(mapSting);

            updateX509 = new UpdateX509();
            updateX509.evaluateTriggers(requiredActionContext);

            // create map - no valid value
            mapSting = new HashMap<>();
            mapSting.put("no valid value", new ArrayList<>());

            when(userModel.getAttributes()).thenReturn(mapSting);
            x509ToolsMock.when(() -> X509Tools.isX509Registered(eq(requiredActionContext))).thenReturn(true);

            updateX509 = new UpdateX509();
            updateX509.evaluateTriggers(requiredActionContext);
        }
    }

    @Test
    public void testEvaluateTriggersCondition2() throws Exception {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            // null, not null, not true
            when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);

            x509ToolsMock.when(() -> X509Tools.getX509Username(eq(requiredActionContext))).thenReturn(null);
            when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn("authNote");

            UpdateX509 updateX509 = new UpdateX509();
            updateX509.evaluateTriggers(requiredActionContext);

            // null, null, not true
            when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn(null);
            updateX509.evaluateTriggers(requiredActionContext);

            // null, not null, true
            when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn("true");
            updateX509.evaluateTriggers(requiredActionContext);

            // something, not null, true
            x509ToolsMock.when(() -> X509Tools.getX509Username(eq(requiredActionContext))).thenReturn("something");
            updateX509.evaluateTriggers(requiredActionContext);
        }
    }

    @Test
    public void testRequiredActionChallengeCondition1() throws Exception {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            x509ToolsMock.when(() -> X509Tools.getX509Username(any(RequiredActionContext.class))).thenReturn("thing");

            when(requiredActionContext.form()).thenReturn(loginFormsProvider);

            UpdateX509 updateX509 = new UpdateX509();
            updateX509.requiredActionChallenge(requiredActionContext);
        }
    }

    @Test
    public void testRequiredActionChallengeCondition2() throws Exception {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            x509ToolsMock.when(() -> X509Tools.getX509Username(any(RequiredActionContext.class))).thenReturn("thing");

            when(requiredActionContext.form()).thenReturn(loginFormsProvider);
            when(requiredActionContext.getUser()).thenReturn(userModel);
            when(userModel.getUsername()).thenReturn("an awesome username");

            UpdateX509 updateX509 = new UpdateX509();
            updateX509.requiredActionChallenge(requiredActionContext);
        }
    }

    @Test
    public void testProcessActionCancel() throws Exception {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            x509ToolsMock.when(() -> X509Tools.getX509Username(any(RequiredActionContext.class))).thenReturn("thing");

            Map<String, List<String>> formDataMap = new HashMap<>();
            formDataMap.put("cancel", Collections.singletonList(""));

            when(requiredActionContext.getHttpRequest().getDecodedFormParameters()).thenReturn(Utils.formDataUtil(formDataMap));
            when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);

            UpdateX509 updateX509 = new UpdateX509();
            updateX509.processAction(requiredActionContext);
        }
    }

    @Test
    public void testProcessAction() throws Exception {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            x509ToolsMock.when(() -> X509Tools.getX509Username(any(RequiredActionContext.class))).thenReturn(null);

            Map<String, List<String>> formDataMap = new HashMap<>();

            when(requiredActionContext.getHttpRequest().getDecodedFormParameters()).thenReturn(Utils.formDataUtil(formDataMap));
            when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
            when(requiredActionContext.getUser()).thenReturn(userModel);

            UpdateX509 updateX509 = new UpdateX509();
            updateX509.processAction(requiredActionContext);

            // CONDITION 2
            x509ToolsMock.when(() -> X509Tools.getX509Username(eq(requiredActionContext))).thenReturn("something");

            updateX509.processAction(requiredActionContext);
        }
    }

    @Test
    public void testInit(){
        UpdateX509 updateX509 = new UpdateX509();
        updateX509.init(scope);
    }

    @Test
    public void testGetDisplayText() {
        UpdateX509 updateX509 = new UpdateX509();
        updateX509.getDisplayText();
    }

    @Test
    public void testIsOneTimeAction() {
        UpdateX509 updateX509 = new UpdateX509();
        updateX509.isOneTimeAction();
    }

    @Test
    public void testCreate() {
        UpdateX509 updateX509 = new UpdateX509();
        updateX509.create(keycloakSession);
    }

    @Test
    public void testPostInit() {
        UpdateX509 updateX509 = new UpdateX509();
        updateX509.postInit(keycloakSession.getKeycloakSessionFactory());
    }

    @Test
    public void testClose() {
        UpdateX509 updateX509 = new UpdateX509();
        updateX509.close();
    }

    @Test
    public void testGetId() {
        UpdateX509 updateX509 = new UpdateX509();
        updateX509.getId();
    }
}
