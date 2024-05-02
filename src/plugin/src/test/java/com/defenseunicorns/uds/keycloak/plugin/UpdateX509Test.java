package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.http.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.common.crypto.UserIdentityExtractor;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.authentication.authenticators.x509.X509AuthenticatorConfigModel;
import org.keycloak.authentication.authenticators.x509.X509ClientCertificateAuthenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.services.x509.X509ClientCertificateLookup;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.defenseunicorns.uds.keycloak.plugin.utils.Utils;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupFileMocks;
import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupX509Mocks;
import static org.mockito.ArgumentMatchers.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;


@RunWith(PowerMockRunner.class)
@PrepareForTest({ X509Tools.class })
//@PowerMockIgnore("javax.management.*")
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.net.ssl.*", "org.slf4j.*", "javax.parsers.*", "ch.qos.logback.*", "jdk.xml.internal.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
//@PowerMockIgnore("jdk.internal.reflect.*")
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

    @Before
    public void setupMockBehavior() throws Exception {

        setupFileMocks();

        // common mock implementations
        PowerMockito.when(requiredActionContext.getSession()).thenReturn(keycloakSession);
        PowerMockito.when(keycloakSession.getContext()).thenReturn(keycloakContext);
        PowerMockito.when(keycloakSession.getContext().getAuthenticationSession()).thenReturn(authenticationSessionModel);
        PowerMockito.when(authenticationSessionModel.getParentSession()).thenReturn(rootAuthenticationSessionModel);
        PowerMockito.when(rootAuthenticationSessionModel.getId()).thenReturn("xxx");
        PowerMockito.when(requiredActionContext.getHttpRequest()).thenReturn(httpRequest);
        PowerMockito.when(requiredActionContext.getRealm()).thenReturn(realmModel);

        // setup X509Tools
        PowerMockito.when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        // create cert array and add the cert
        X509Certificate[] certList = new X509Certificate[1];
        X509Certificate x509Certificate2 = Utils.buildTestCertificate();
        certList[0] = x509Certificate2;
        PowerMockito.when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certList);

        PowerMockito.when(realmModel.getAuthenticatorConfigsStream()).thenAnswer((stream) -> {
            return Stream.of(authenticatorConfigModel);
        });

        // create map
        Map<String, String> mapSting = new HashMap<>();
        mapSting.put("x509-cert-auth.mapper-selection.user-attribute-name", "test");
        PowerMockito.when(authenticatorConfigModel.getConfig()).thenReturn(mapSting);

        PowerMockito.when(x509ClientCertificateAuthenticator
                .getUserIdentityExtractor(any(X509AuthenticatorConfigModel.class))).thenReturn(userIdentityExtractor);
        PowerMockito.when(keycloakSession.users()).thenReturn(userProvider);
        PowerMockito.when(userProvider.searchForUserByUserAttributeStream(any(RealmModel.class), anyString(), anyString()))
                .thenAnswer((stream) -> {
                    return Stream.of(userModel);
                });

        CryptoIntegration.init(this.getClass().getClassLoader());
    }

    @Test
    public void testEvaluateTriggersCondition1() throws Exception {
        mockStatic(X509Tools.class);
        PowerMockito.when(X509Tools.getX509Username(eq(requiredActionContext))).thenReturn("something");

        PowerMockito.when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
        PowerMockito.when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn("authNote");

        // create cert array and add the cert
        X509Certificate[] certList = new X509Certificate[1];
        X509Certificate x509Certificate2 = Utils.buildTestCertificate();
        certList[0] = x509Certificate2;

        // create map - some value
        Map<String, List<String>> mapSting = new HashMap<>();
        List<String> listString = new ArrayList<>();
        listString.add("some value");
        mapSting.put("usercertificate", listString);

        PowerMockito.when(requiredActionContext.getHttpRequest().getClientCertificateChain())
            .thenReturn(certList);
        PowerMockito.when(requiredActionContext.getUser()).thenReturn(userModel);
        PowerMockito.when(userModel.getAttributes()).thenReturn(mapSting);

        UpdateX509 updateX509 = new UpdateX509();
        updateX509.evaluateTriggers(requiredActionContext);

        // create map - empty value
        mapSting = new HashMap<>();
        mapSting.put("usercertificate", new ArrayList<>());

        PowerMockito.when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn(null);
        PowerMockito.when(userModel.getAttributes()).thenReturn(mapSting);

        updateX509 = new UpdateX509();
        updateX509.evaluateTriggers(requiredActionContext);

        // create map - null value
        mapSting = new HashMap<>();
        mapSting.put("usercertificate", null);

        PowerMockito.when(userModel.getAttributes()).thenReturn(mapSting);

        updateX509 = new UpdateX509();
        updateX509.evaluateTriggers(requiredActionContext);

        // create map - no valid value
        mapSting = new HashMap<>();
        mapSting.put("no valid value", new ArrayList<>());

        PowerMockito.when(userModel.getAttributes()).thenReturn(mapSting);
        PowerMockito.when(X509Tools.isX509Registered(eq(requiredActionContext))).thenReturn(true);

        updateX509 = new UpdateX509();
        updateX509.evaluateTriggers(requiredActionContext);
    }

    @Test
    public void testEvaluateTriggersCondition2() throws Exception {
        // null, not null, not true
        mockStatic(X509Tools.class);
        PowerMockito.when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);

        PowerMockito.when(X509Tools.getX509Username(eq(requiredActionContext))).thenReturn(null);
        PowerMockito.when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn("authNote");

        UpdateX509 updateX509 = new UpdateX509();
        updateX509.evaluateTriggers(requiredActionContext);

        // null, null, not true
        PowerMockito.when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn(null);
        updateX509.evaluateTriggers(requiredActionContext);

        // null, not null, true
        PowerMockito.when(requiredActionContext.getAuthenticationSession().getAuthNote("IGNORE_X509")).thenReturn("true");
        updateX509.evaluateTriggers(requiredActionContext);

        // something, not null, true
        PowerMockito.when(X509Tools.getX509Username(eq(requiredActionContext))).thenReturn("something");
        updateX509.evaluateTriggers(requiredActionContext);
    }

    @Test
    public void testRequiredActionChallengeCondition1() throws Exception {
        setupX509Mocks();

        PowerMockito.when(requiredActionContext.form()).thenReturn(loginFormsProvider);

        UpdateX509 updateX509 = new UpdateX509();
        updateX509.requiredActionChallenge(requiredActionContext);
    }

    @Test
    public void testRequiredActionChallengeCondition2() throws Exception {
        setupX509Mocks();

        PowerMockito.when(requiredActionContext.form()).thenReturn(loginFormsProvider);
        PowerMockito.when(requiredActionContext.getUser()).thenReturn(userModel);
        PowerMockito.when(userModel.getUsername()).thenReturn("an awesome username");

        UpdateX509 updateX509 = new UpdateX509();
        updateX509.requiredActionChallenge(requiredActionContext);
    }

    @Test
    public void testProcessActionCancel() throws Exception {
        setupX509Mocks();

        Map<String, List<String>> formDataMap = new HashMap<>();
        formDataMap.put("cancel", Collections.singletonList(""));

        PowerMockito.when(requiredActionContext.getHttpRequest().getDecodedFormParameters()).thenReturn(Utils.formDataUtil(formDataMap));
        PowerMockito.when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);

        UpdateX509 updateX509 = new UpdateX509();
        updateX509.processAction(requiredActionContext);
    }

    @Test
    public void testProcessAction() throws Exception {
        setupX509Mocks();

        Map<String, List<String>> formDataMap = new HashMap<>();

        PowerMockito.when(requiredActionContext.getHttpRequest().getDecodedFormParameters()).thenReturn(Utils.formDataUtil(formDataMap));
        PowerMockito.when(requiredActionContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
        PowerMockito.when(requiredActionContext.getUser()).thenReturn(userModel);

        UpdateX509 updateX509 = new UpdateX509();
        updateX509.processAction(requiredActionContext);

        // CONDITION 2
        mockStatic(X509Tools.class);
        PowerMockito.when(X509Tools.getX509Username(eq(requiredActionContext))).thenReturn("something");

        updateX509.processAction(requiredActionContext);
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
