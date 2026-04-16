/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
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
    private static final String EKS_ISSUER = "https://oidc.eks.us-east-1.amazonaws.com/id/EXAMPLE";
    private static final String EKS_JWKS_URI = EKS_ISSUER + "/keys";

    /** Builds a minimal unsigned JWT (header.payload.signature) with the given issuer claim. */
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

    @Test
    void testLoadKeysSameOriginTokenIncluded() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setJwksUri(JWKS_URI);

            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asJson(OIDCConfigurationRepresentation.class)).thenReturn(oidcConfig);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);

            when(mockHttp.doGet(JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asJson(JSONWebKeySet.class)).thenReturn(jwks);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(K8S_SA_TOKEN);
            verify(jwksRequest).auth(K8S_SA_TOKEN);
        }
    }

    @Test
    void testLoadKeysEksSameOriginTokenIncluded() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            // EKS token: iss matches the EKS JWKS URI origin
            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(EKS_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setJwksUri(EKS_JWKS_URI);

            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asJson(OIDCConfigurationRepresentation.class)).thenReturn(oidcConfig);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);

            when(mockHttp.doGet(EKS_JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asJson(JSONWebKeySet.class)).thenReturn(jwks);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(EKS_SA_TOKEN);
            // Token issuer matches JWKS URI origin — token IS included
            verify(jwksRequest).auth(EKS_SA_TOKEN);
        }
    }

    @Test
    void testLoadKeysCrossOriginTokenNotIncluded() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            // K8s-issued token (iss=kubernetes.default.svc) but JWKS URI points to EKS
            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setJwksUri(EKS_JWKS_URI);

            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asJson(OIDCConfigurationRepresentation.class)).thenReturn(oidcConfig);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);

            when(mockHttp.doGet(EKS_JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asJson(JSONWebKeySet.class)).thenReturn(jwks);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(K8S_SA_TOKEN);
            // Token issuer does NOT match JWKS URI origin — token is NOT sent
            verify(jwksRequest, never()).auth(anyString());
        }
    }

    @Test
    void testLoadKeysThrowsOnNullJwksUri() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();

            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asJson(OIDCConfigurationRepresentation.class)).thenReturn(oidcConfig);

            IllegalStateException ex = assertThrows(IllegalStateException.class, loader::loadKeys);
            assertTrue(ex.getMessage().contains("returned no jwks_uri"));
        }
    }

    @Test
    void testLoadKeysThrowsOnEmptyJwksUri() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(K8S_SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setJwksUri("");

            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asJson(OIDCConfigurationRepresentation.class)).thenReturn(oidcConfig);

            IllegalStateException ex = assertThrows(IllegalStateException.class, loader::loadKeys);
            assertTrue(ex.getMessage().contains("returned no jwks_uri"));
        }
    }

    @Test
    void testLoadKeysSkipsAuthWhenNoSaToken() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(null);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setJwksUri(JWKS_URI);

            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asJson(OIDCConfigurationRepresentation.class)).thenReturn(oidcConfig);

            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);

            when(mockHttp.doGet(JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asJson(JSONWebKeySet.class)).thenReturn(jwks);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest, never()).auth(anyString());
            verify(jwksRequest, never()).auth(anyString());
        }
    }

    @Test
    void testShouldIncludeTokenSameOrigin() {
        assertTrue(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(
            K8S_SA_TOKEN, JWKS_URI));
    }

    @Test
    void testShouldIncludeTokenEksSameOrigin() {
        assertTrue(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(
            EKS_SA_TOKEN, EKS_JWKS_URI));
    }

    @Test
    void testShouldIncludeTokenCrossOrigin() {
        assertFalse(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(
            K8S_SA_TOKEN, EKS_JWKS_URI));
    }

    @Test
    void testShouldIncludeTokenMalformedToken() {
        assertFalse(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(
            "not-a-jwt", JWKS_URI));
    }

    @Test
    void testShouldIncludeTokenNoIssuer() {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"test\"}".getBytes());
        String tokenWithoutIss = header + "." + payload + ".";

        assertFalse(UDSKubernetesJwksEndpointLoader.shouldIncludeToken(
            tokenWithoutIss, JWKS_URI));
    }
}
