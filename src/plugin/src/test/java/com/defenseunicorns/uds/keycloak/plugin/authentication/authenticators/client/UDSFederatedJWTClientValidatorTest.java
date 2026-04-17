/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.AbstractJWTClientValidator.SignatureValidator;
import org.keycloak.authentication.authenticators.client.ClientAssertionState;
import org.keycloak.authentication.authenticators.client.ClientAuthUtil;
import org.keycloak.common.util.Time;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.HttpRequest;
import org.keycloak.jose.jws.JWSHeader;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.services.Urls;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UDSFederatedJWTClientValidatorTest {

    private static final String EXPECTED_ISSUER = "https://kubernetes.default.svc.cluster.local";
    private static final String REALM_NAME = "uds";
    private static final String REALM_ISSUER_URL = "https://keycloak.admin.uds.dev/realms/uds";
    private static final String CLIENT_ID = "uds-operator";
    private static final String SUBJECT = "system:serviceaccount:pepr-system:pepr-uds-core";

    @Mock private ClientAuthenticationFlowContext context;
    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    @Mock private ClientModel clientModel;
    @Mock private ClientAssertionState state;
    @Mock private JsonWebToken token;
    @Mock private JWSInput jws;
    @Mock private JWSHeader header;
    @Mock private HttpRequest httpRequest;
    @Mock private EventBuilder event;
    @Mock private UriInfo uriInfo;
    @Mock private SignatureValidator signatureValidator;

    private MultivaluedMap<String, String> formParams;
    private MockedStatic<Urls> urlsMock;
    private MockedStatic<ClientAuthUtil> clientAuthUtilMock;

    @BeforeEach
    void setup() throws Exception {
        formParams = new MultivaluedHashMap<>();

        // Mock static methods that require JAX-RS RuntimeDelegate
        urlsMock = mockStatic(Urls.class);
        urlsMock.when(() -> Urls.realmIssuer(any(URI.class), anyString())).thenReturn(REALM_ISSUER_URL);

        clientAuthUtilMock = mockStatic(ClientAuthUtil.class);
        clientAuthUtilMock.when(() -> ClientAuthUtil.errorResponse(anyInt(), anyString(), anyString()))
            .thenReturn(mock(Response.class));

        // Context mocks
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getState(eq(ClientAssertionState.class), any())).thenReturn(state);
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(context.getEvent()).thenReturn(event);
        when(context.getUriInfo()).thenReturn(uriInfo);

        // Realm
        when(realm.getName()).thenReturn(REALM_NAME);

        // UriInfo for audience validation
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://keycloak.admin.uds.dev"));

        // HTTP Request
        when(httpRequest.getDecodedFormParameters()).thenReturn(formParams);

        // ClientAssertionState - happy path
        when(state.getClientAssertionType()).thenReturn(OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT);
        when(state.getClientAssertion()).thenReturn("dummy.jwt.assertion");
        when(state.getToken()).thenReturn(token);
        when(state.getJws()).thenReturn(jws);
        when(state.getClient()).thenReturn(clientModel);

        // Token claims - happy path
        when(token.getSubject()).thenReturn(SUBJECT);
        when(token.getIssuer()).thenReturn(EXPECTED_ISSUER);
        when(token.getExp()).thenReturn((long) Time.currentTime() + 600);
        when(token.isActive(anyInt())).thenReturn(true);
        when(token.getIat()).thenReturn(null);
        when(token.hasAnyAudience(anyList())).thenReturn(true);
        when(token.getAudience()).thenReturn(new String[]{REALM_ISSUER_URL});

        // JWS header - non-null algorithm required by validateSignatureAlgorithm
        when(jws.getHeader()).thenReturn(header);
        when(header.getAlgorithm()).thenReturn(org.keycloak.jose.jws.Algorithm.RS256);

        // Client model
        when(clientModel.isEnabled()).thenReturn(true);
        when(clientModel.getClientId()).thenReturn(CLIENT_ID);

        // Signature validator
        when(signatureValidator.verifySignature(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        urlsMock.close();
        clientAuthUtilMock.close();
    }

    @Test
    void testValidateSucceedsWhenAllChecksPass() throws Exception {
        UDSFederatedJWTClientValidator validator = createValidator();
        assertTrue(validator.validate());
    }

    @Test
    void testValidateSucceedsWhenClientIdParamMismatch() throws Exception {
        // client_id form param (uds-operator) differs from JWT subject (SA name)
        // UDS validator logs debug and continues instead of failing
        formParams.putSingle(OAuth2Constants.CLIENT_ID, "uds-operator");

        UDSFederatedJWTClientValidator validator = createValidator();
        assertTrue(validator.validate());
    }

    @Test
    void testValidateFailsWhenSubjectMissing() throws Exception {
        when(token.getSubject()).thenReturn(null);

        UDSFederatedJWTClientValidator validator = createValidator();
        assertFalse(validator.validate());
    }

    @Test
    void testValidateFailsWhenIssuerMismatch() throws Exception {
        when(token.getIssuer()).thenReturn("https://centralus.oic.prod-aks.azure.com/different");

        UDSFederatedJWTClientValidator validator = createValidator();
        assertFalse(validator.validate());
    }

    @Test
    void testValidateFailsWhenClientNotFound() throws Exception {
        when(state.getClient()).thenReturn(null);

        UDSFederatedJWTClientValidator validator = createValidator();
        assertFalse(validator.validate());
        verify(context).failure(eq(AuthenticationFlowError.CLIENT_NOT_FOUND), isNull());
    }

    @Test
    void testValidateFailsWhenClientDisabled() throws Exception {
        when(clientModel.isEnabled()).thenReturn(false);

        UDSFederatedJWTClientValidator validator = createValidator();
        assertFalse(validator.validate());
        verify(context).failure(eq(AuthenticationFlowError.CLIENT_DISABLED), isNull());
    }

    @Test
    void testValidateFailsWhenSignatureInvalid() throws Exception {
        when(signatureValidator.verifySignature(any())).thenReturn(false);

        UDSFederatedJWTClientValidator validator = createValidator();
        assertFalse(validator.validate());
    }

    @Test
    void testValidateFailsWhenAssertionTypeMissing() throws Exception {
        when(state.getClientAssertionType()).thenReturn(null);

        UDSFederatedJWTClientValidator validator = createValidator();
        assertFalse(validator.validate());
    }

    @Test
    void testValidateFailsWhenAssertionMissing() throws Exception {
        when(state.getClientAssertion()).thenReturn(null);

        UDSFederatedJWTClientValidator validator = createValidator();
        assertFalse(validator.validate());
    }

    private UDSFederatedJWTClientValidator createValidator() throws Exception {
        UDSFederatedJWTClientValidator validator = new UDSFederatedJWTClientValidator(
            context, signatureValidator, EXPECTED_ISSUER, 0, true
        );
        validator.setMaximumExpirationTime(3600);
        return validator;
    }
}
