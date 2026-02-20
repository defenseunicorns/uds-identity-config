/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.utils.UserModelDefaultMethodsImpl;
import com.defenseunicorns.uds.keycloak.plugin.utils.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.authenticators.x509.X509AuthenticatorConfigModel;
import org.keycloak.authentication.authenticators.x509.X509ClientCertificateAuthenticator;
import org.keycloak.common.crypto.UserIdentityExtractor;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.services.validation.Validation;
import org.keycloak.services.x509.X509ClientCertificateLookup;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RegistrationValidation2Test {

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
    ValidationContext validationContext;
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
    GroupProvider groupProvider;

    public RegistrationValidation2Test() {
    }

    @BeforeEach
    public void setupMockBehavior() throws Exception {
        // common mock implementations
        when(validationContext.getSession()).thenReturn(keycloakSession);
        when(keycloakSession.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
        when(authenticationSessionModel.getParentSession()).thenReturn(rootAuthenticationSessionModel);
        when(rootAuthenticationSessionModel.getId()).thenReturn("xxx");
        when(validationContext.getHttpRequest()).thenReturn(httpRequest);
        when(validationContext.getRealm()).thenReturn(realmModel);
        when(keycloakSession.groups()).thenReturn(groupProvider);

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
    public void testSuccess() {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            x509ToolsMock.when(() -> X509Tools.getX509Username(any(FormContext.class))).thenReturn("something");
            x509ToolsMock.when(() -> X509Tools.getX509CommonName(any(FormContext.class))).thenReturn(null);

            UserModelDefaultMethodsImpl userModelDefaultMethodsImpl = new UserModelDefaultMethodsImpl();
            when(validationContext.getUser()).thenReturn(userModelDefaultMethodsImpl);
            when(validationContext.getRealm()).thenReturn(realmModel);

            // Populate form data
            Map<String, List<String>> formDataMap = new HashMap<>();
            formDataMap.put(Validation.FIELD_EMAIL, Collections.singletonList("test.user@test.bad"));

            when(validationContext.getHttpRequest().getDecodedFormParameters()).thenReturn(Utils.formDataUtil(formDataMap));

            RegistrationValidation registrationValidation = new RegistrationValidation();
            registrationValidation.success(validationContext);
        }
    }

    @Test
    public void testSuccessNoX509() throws GeneralSecurityException {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            // Force no certificate
            when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(null);
            x509ToolsMock.when(() -> X509Tools.getX509Username(any(FormContext.class))).thenReturn(null);
            x509ToolsMock.when(() -> X509Tools.getX509CommonName(any(FormContext.class))).thenReturn(null);

            // Mock user and realm
            UserModelDefaultMethodsImpl userModelDefaultMethodsImpl = new UserModelDefaultMethodsImpl();
            when(validationContext.getUser()).thenReturn(userModelDefaultMethodsImpl);
            when(validationContext.getRealm()).thenReturn(realmModel);

            // Populate form data
            Map<String, List<String>> formDataMap = new HashMap<>();
            formDataMap.put(Validation.FIELD_EMAIL, Collections.singletonList("test.user@test.bad"));

            // Mock the behavior to return the populated form data
            when(validationContext.getHttpRequest().getDecodedFormParameters()).thenReturn(Utils.formDataUtil(formDataMap));

            // Call the method under test
            RegistrationValidation registrationValidation = new RegistrationValidation();
            registrationValidation.success(validationContext);
        }
    }
}
