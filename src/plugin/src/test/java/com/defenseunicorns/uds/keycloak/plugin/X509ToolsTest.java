/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.utils.Utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.services.x509.X509ClientCertificateLookup;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;

import java.security.cert.X509Certificate;

import static com.defenseunicorns.uds.keycloak.plugin.X509Tools.isX509Registered;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class X509ToolsTest {

    @Mock
    private KeycloakSession keycloakSession;

    @Mock
    private KeycloakContext keycloakContext;

    @Mock
    private AuthenticationSessionModel authenticationSessionModel;

    @Mock
    private RootAuthenticationSessionModel rootAuthenticationSessionModel;

    @Mock
    private HttpRequest httpRequest;

    @Mock
    private RealmModel realmModel;

    @Mock
    private ValidationContext validationContext;

    @Mock
    private X509ClientCertificateLookup x509ClientCertificateLookup;

    @Mock
    private FormContext formContext;

    @Mock
    private RequiredActionContext requiredActionContext;

    @Mock
    private UserProvider userProvider;

    @Mock
    private UserModel userModel;

    @InjectMocks
    private X509Tools x509Tools;


    @Before
    public void setupMockBehavior() throws Exception {
        when(keycloakSession.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
        when(authenticationSessionModel.getParentSession()).thenReturn(rootAuthenticationSessionModel);

        PowerMockito.when(validationContext.getSession()).thenReturn(keycloakSession);

        when(formContext.getSession()).thenReturn(keycloakSession);
        when(formContext.getHttpRequest()).thenReturn(httpRequest);
        when(formContext.getRealm()).thenReturn(realmModel);

        when(requiredActionContext.getSession()).thenReturn(keycloakSession);
        when(requiredActionContext.getHttpRequest()).thenReturn(httpRequest);
        when(requiredActionContext.getRealm()).thenReturn(realmModel);
    }

    @Test
    public void testIsX509RegisteredFalse() {
        boolean isRegistered = isX509Registered(validationContext);
        Assert.assertFalse(isRegistered);
    }

    @Test
    public void testGetX509CommonNameFromFormContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String commonName = X509Tools.getX509CommonName(formContext);
        Assert.assertEquals("login.dso.mil", commonName);
    }

    @Test
    public void testGetX509CommonNameFromFormContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String commonName = X509Tools.getX509CommonName(formContext);
        Assert.assertNull(commonName);
    }

    @Test
    public void testGetX509CommonNameFromRequiredActionContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String commonName = X509Tools.getX509CommonName(requiredActionContext);
        Assert.assertEquals("login.dso.mil", commonName);
    }

    @Test
    public void testGetX509CommonNameFromRequiredActionContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String commonName = X509Tools.getX509CommonName(requiredActionContext);
        Assert.assertNull(commonName);
    }

    @Test
    public void testGetX509SubjectDNFromFormContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String subjectDN = X509Tools.getX509SubjectDN(formContext);
        Assert.assertEquals("CN=login.dso.mil,O=Department of Defense,L=Colorado Springs,ST=Colorado,C=US", subjectDN);
    }

    @Test
    public void testGetX509SubjectDNFromFormContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String subjectDN = X509Tools.getX509SubjectDN(formContext);
        Assert.assertNull(subjectDN);
    }

    @Test
    public void testGetX509SubjectDNFromRequiredActionContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String subjectDN = X509Tools.getX509SubjectDN(requiredActionContext);
        Assert.assertEquals("CN=login.dso.mil,O=Department of Defense,L=Colorado Springs,ST=Colorado,C=US", subjectDN);
    }

    @Test
    public void testGetX509SubjectDNFromRequiredActionContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String subjectDN = X509Tools.getX509SubjectDN(requiredActionContext);
        Assert.assertNull(subjectDN);
    }

    @Test
    public void testIsX509NotRegisteredFromFormContext() throws Exception {
        boolean isRegistered = X509Tools.isX509Registered(formContext);
        Assert.assertFalse(isRegistered);
    }

    @Test
    public void testIsX509NotRegisteredFromRequiredActionContext() throws Exception {
        boolean isRegistered = X509Tools.isX509Registered(requiredActionContext);
        Assert.assertFalse(isRegistered);
    }
}
