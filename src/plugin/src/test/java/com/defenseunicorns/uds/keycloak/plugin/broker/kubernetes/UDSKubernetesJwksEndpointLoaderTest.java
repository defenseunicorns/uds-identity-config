/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.util.JsonSerialization;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UDSKubernetesJwksEndpointLoaderTest {

    private static final String ISSUER = "https://kubernetes.default.svc.cluster.local";
    private static final String WELL_KNOWN = ISSUER + "/.well-known/openid-configuration";
    private static final String JWKS_URI = ISSUER + "/openid/v1/jwks";
    private static final String K3D_JWKS_URI = "https://172.18.0.3:6443/openid/v1/jwks";
    private static final String EKS_ISSUER = "https://oidc.eks.us-east-1.amazonaws.com/id/EXAMPLE";
    private static final String EKS_JWKS_URI = EKS_ISSUER + "/keys";

    private static String buildTokenWithIssuer(String issuer) {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("{\"iss\":\"" + issuer + "\",\"sub\":\"system:serviceaccount:ns:sa\"}").getBytes());
        return header + "." + payload + ".";
    }

    private static final String K8S_SA_TOKEN = buildTokenWithIssuer(ISSUER);
    private static final String EKS_SA_TOKEN = buildTokenWithIssuer(EKS_ISSUER);

    @Mock private KeycloakSession session;

    private SimpleHttpResponse buildResponse(int status, Object body) throws Exception {
        SimpleHttpResponse response = mock(SimpleHttpResponse.class);
        when(response.getStatus()).thenReturn(status);
        if (body != null) {
            when(response.asString()).thenReturn(JsonSerialization.writeValueAsString(body));
        }
        return response;
    }

    private SimpleHttpResponse buildResponse(int status) throws Exception {
        return buildResponse(status, null);
    }

    // --- loadKeys integration tests ---

    @Test
    void testLoadKeysVanillaK8s() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setIssuer(ISSUER);
            oidcConfig.setJwksUri(JWKS_URI);
            SimpleHttpResponse wellKnownResponse = buildResponse(200, oidcConfig);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);
            SimpleHttpResponse jwksResponse = buildResponse(200, jwks);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asResponse()).thenReturn(jwksResponse);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(K8S_SA_TOKEN);
            verify(jwksRequest).auth(K8S_SA_TOKEN);
        }
    }

    @Test
    void testLoadKeysK3dWithIpJwksUri() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setIssuer(ISSUER);
            oidcConfig.setJwksUri(K3D_JWKS_URI);
            SimpleHttpResponse wellKnownResponse = buildResponse(200, oidcConfig);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);
            SimpleHttpResponse jwksResponse = buildResponse(200, jwks);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(K3D_JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asResponse()).thenReturn(jwksResponse);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(K8S_SA_TOKEN);
            verify(jwksRequest).auth(K8S_SA_TOKEN);
        }
    }

    @Test
    void testLoadKeysEksTokenIncluded() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(EKS_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setIssuer(EKS_ISSUER);
            oidcConfig.setJwksUri(EKS_JWKS_URI);
            SimpleHttpResponse wellKnownResponse = buildResponse(200, oidcConfig);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);
            SimpleHttpResponse jwksResponse = buildResponse(200, jwks);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(EKS_JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asResponse()).thenReturn(jwksResponse);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(EKS_SA_TOKEN);
            verify(jwksRequest).auth(EKS_SA_TOKEN);
        }
    }

    @Test
    void testLoadKeysCrossIssuerTokenNotIncluded() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setIssuer(EKS_ISSUER);
            oidcConfig.setJwksUri(EKS_JWKS_URI);
            SimpleHttpResponse wellKnownResponse = buildResponse(200, oidcConfig);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);
            SimpleHttpResponse jwksResponse = buildResponse(200, jwks);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(EKS_JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asResponse()).thenReturn(jwksResponse);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(K8S_SA_TOKEN);
            verify(jwksRequest, never()).auth(anyString());
        }
    }

    @Test
    void testLoadKeysThrowsOnNullJwksUri() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setIssuer(ISSUER);
            SimpleHttpResponse wellKnownResponse = buildResponse(200, oidcConfig);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            IllegalStateException ex = assertThrows(IllegalStateException.class, loader::loadKeys);
            assertTrue(ex.getMessage().contains("returned no jwks_uri"));
        }
    }

    @Test
    void testLoadKeysThrowsOnWellKnownNon200() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            SimpleHttpResponse wellKnownResponse = buildResponse(401);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            IllegalStateException ex = assertThrows(IllegalStateException.class, loader::loadKeys);
            assertTrue(ex.getMessage().contains("returned HTTP 401"));
        }
    }

    @Test
    void testLoadKeysThrowsOnJwksNon200() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setIssuer(ISSUER);
            oidcConfig.setJwksUri(JWKS_URI);
            SimpleHttpResponse wellKnownResponse = buildResponse(200, oidcConfig);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            SimpleHttpResponse jwksResponse = buildResponse(403);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asResponse()).thenReturn(jwksResponse);

            IllegalStateException ex = assertThrows(IllegalStateException.class, loader::loadKeys);
            assertTrue(ex.getMessage().contains("returned HTTP 403"));
        }
    }

    @Test
    void testLoadKeysSkipsAuthWhenNoSaToken() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(null);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setIssuer(ISSUER);
            oidcConfig.setJwksUri(JWKS_URI);
            SimpleHttpResponse wellKnownResponse = buildResponse(200, oidcConfig);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asResponse()).thenReturn(wellKnownResponse);

            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);
            SimpleHttpResponse jwksResponse = buildResponse(200, jwks);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            when(mockHttp.doGet(JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asResponse()).thenReturn(jwksResponse);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest, never()).auth(anyString());
            verify(jwksRequest, never()).auth(anyString());
        }
    }

    // --- shouldIncludeToken unit tests ---

    @Test
    void testShouldIncludeTokenMatchingIssuers() {
        assertTrue(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(K8S_SA_TOKEN, ISSUER));
    }

    @Test
    void testShouldIncludeTokenEksMatchingIssuers() {
        assertTrue(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(EKS_SA_TOKEN, EKS_ISSUER));
    }

    @Test
    void testShouldIncludeTokenMismatchedIssuers() {
        assertFalse(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(K8S_SA_TOKEN, EKS_ISSUER));
    }

    @Test
    void testShouldIncludeTokenMalformedToken() {
        assertFalse(UDSKubernetesJwksEndpointLoader.shouldIncludeToken("not-a-jwt", ISSUER));
    }

    @Test
    void testShouldIncludeTokenNoIssuerInToken() {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"test\"}".getBytes());
        String tokenWithoutIss = header + "." + payload + ".";

        assertFalse(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(tokenWithoutIss, ISSUER));
    }

    @Test
    void testShouldIncludeTokenNullDiscoveredIssuer() {
        assertFalse(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(K8S_SA_TOKEN, null));
    }
}
