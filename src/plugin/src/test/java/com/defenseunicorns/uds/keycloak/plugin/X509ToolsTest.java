/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.utils.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509Certificate;

import static com.defenseunicorns.uds.keycloak.plugin.X509Tools.isX509Registered;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
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


    @BeforeEach
    public void setupMockBehavior() throws Exception {
        when(keycloakSession.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authenticationSessionModel);
        when(authenticationSessionModel.getParentSession()).thenReturn(rootAuthenticationSessionModel);

        when(validationContext.getSession()).thenReturn(keycloakSession);

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
        assertFalse(isRegistered);
    }

    @Test
    public void testGetX509CommonNameFromFormContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String commonName = X509Tools.getX509CommonName(formContext);
        assertEquals("login.dso.mil", commonName);
    }

    @Test
    public void testGetX509CommonNameFromFormContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String commonName = X509Tools.getX509CommonName(formContext);
        assertNull(commonName);
    }

    @Test
    public void testGetX509CommonNameFromRequiredActionContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String commonName = X509Tools.getX509CommonName(requiredActionContext);
        assertEquals("login.dso.mil", commonName);
    }

    @Test
    public void testGetX509CommonNameFromRequiredActionContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String commonName = X509Tools.getX509CommonName(requiredActionContext);
        assertNull(commonName);
    }

    @Test
    public void testGetX509SubjectDNFromFormContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String subjectDN = X509Tools.getX509SubjectDN(formContext);
        assertEquals("CN=login.dso.mil,O=Department of Defense,L=Colorado Springs,ST=Colorado,C=US", subjectDN);
    }

    @Test
    public void testGetX509SubjectDNFromFormContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String subjectDN = X509Tools.getX509SubjectDN(formContext);
        assertNull(subjectDN);
    }

    @Test
    public void testGetX509SubjectDNFromRequiredActionContext() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(x509ClientCertificateLookup);

        X509Certificate cert = Utils.buildTestCertificate();
        X509Certificate[] certs = new X509Certificate[]{cert};
        when(x509ClientCertificateLookup.getCertificateChain(httpRequest)).thenReturn(certs);

        String subjectDN = X509Tools.getX509SubjectDN(requiredActionContext);
        assertEquals("CN=login.dso.mil,O=Department of Defense,L=Colorado Springs,ST=Colorado,C=US", subjectDN);
    }

    @Test
    public void testGetX509SubjectDNFromRequiredActionContextNull() throws Exception {
        when(keycloakSession.getProvider(X509ClientCertificateLookup.class)).thenReturn(null);

        String subjectDN = X509Tools.getX509SubjectDN(requiredActionContext);
        assertNull(subjectDN);
    }

    @Test
    public void testIsX509NotRegisteredFromFormContext() throws Exception {
        boolean isRegistered = X509Tools.isX509Registered(formContext);
        assertFalse(isRegistered);
    }

    @Test
    public void testIsX509NotRegisteredFromRequiredActionContext() throws Exception {
        boolean isRegistered = X509Tools.isX509Registered(requiredActionContext);
        assertFalse(isRegistered);
    }

    @Test
    public void testParseCACInfoLastNameFirstNameFormat() {
        String subjectDN = "CN=login.dso.mil,O=Department of Defense";
        String commonName = "doe.john";
        String email = "john.doe@dso.mil";

        CACInfo info = X509Tools.parseCACInfo(subjectDN, commonName, email);

        assertEquals(subjectDN, info.subjectDN());
        assertEquals("John", info.firstName());
        assertEquals("Doe", info.lastName());
        assertEquals(email, info.email());
    }

    @Test
    public void testParseCACInfoOnlyFirstName() {
        String subjectDN = "CN=example";
        String commonName = "jane"; // no last name
        String email = "jane@example.mil";

        CACInfo info = X509Tools.parseCACInfo(subjectDN, commonName, email);

        assertEquals(email, info.email());
        assertNull(info.firstName());
        assertNull(info.lastName());
    }

    @Test
    public void testParseCACInfoNullOrBlankCommonName() {
        String subjectDN = "CN=example";
        String email = "noreply@example.mil";

        // null commonName
        CACInfo infoNull = X509Tools.parseCACInfo(subjectDN, null, email);
        assertNull(infoNull.firstName());
        assertNull(infoNull.lastName());

        // blank commonName
        CACInfo infoBlank = X509Tools.parseCACInfo(subjectDN, "   ", email);
        assertNull(infoBlank.firstName());
        assertNull(infoBlank.lastName());
    }

    @Test
    public void testParseCACInfoWithMiddleName() {
        String subjectDN = "/C=US/O=U.S. Government/OU=CONTRACTOR/OU=DoD/OU=PKI/CN=UNICORN.DOUG.ROCKSTAR.1234567890";
        String commonName = "UNICORN.DOUG.ROCKSTAR.1234567890";

        CACInfo info = X509Tools.parseCACInfo(subjectDN, commonName, null);

        assertEquals("Doug", info.firstName());
        assertEquals("Unicorn", info.lastName());
        assertEquals(subjectDN, info.subjectDN());
    }

    @Test
    public void testParseCACInfoWithNoMiddleName() {
        String subjectDN = "/C=US/O=U.S. Government/OU=CONTRACTOR/OU=DoD/OU=PKI/CN=UNICORN.DOUG.1234567890";
        String commonName = "UNICORN.DOUG.1234567890";

        CACInfo info = X509Tools.parseCACInfo(subjectDN, commonName, "unicorn.doug@example.mil");

        assertEquals("Doug", info.firstName());
        assertEquals("Unicorn", info.lastName());
    }

    @Test
    public void testParseCACInfoWithNoEDI() {
        String subjectDN = "/C=US/O=U.S. Government/OU=CONTRACTOR/OU=DoD/OU=PKI/CN=UNICORN.DOUG";
        String commonName = "UNICORN.DOUG";

        CACInfo info = X509Tools.parseCACInfo(subjectDN, commonName, null);

        assertEquals("Doug", info.firstName());
        assertEquals("Unicorn", info.lastName());
    }
}
