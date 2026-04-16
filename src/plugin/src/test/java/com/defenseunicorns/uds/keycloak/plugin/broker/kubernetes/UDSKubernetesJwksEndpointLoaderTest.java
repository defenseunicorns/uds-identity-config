/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private static final String SA_TOKEN = "my-sa-token";

    @Mock private KeycloakSession session;

    @Test
    void testLoadKeysReturnsKeysFromJwksEndpoint() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            // Mock OIDC discovery endpoint
            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            oidcConfig.setJwksUri(JWKS_URI);

            when(mockHttp.doGet(WELL_KNOWN)).thenReturn(wellKnownRequest);
            when(wellKnownRequest.acceptJson()).thenReturn(wellKnownRequest);
            when(wellKnownRequest.asJson(OIDCConfigurationRepresentation.class)).thenReturn(oidcConfig);

            // Mock JWKS endpoint
            SimpleHttpRequest jwksRequest = mock(SimpleHttpRequest.class);
            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[0]);

            when(mockHttp.doGet(JWKS_URI)).thenReturn(jwksRequest);
            when(jwksRequest.header(anyString(), anyString())).thenReturn(jwksRequest);
            when(jwksRequest.asJson(JSONWebKeySet.class)).thenReturn(jwks);

            PublicKeysWrapper result = loader.loadKeys();

            assertNotNull(result);
            verify(wellKnownRequest).auth(SA_TOKEN);
            verify(jwksRequest).auth(SA_TOKEN);
        }
    }

    @Test
    void testLoadKeysThrowsOnNullJwksUri() throws Exception {
        UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> kubeUtilsMock = mockStatic(KubernetesUtils.class)) {

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(SA_TOKEN);

            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);

            // Mock OIDC discovery returning no jwks_uri
            SimpleHttpRequest wellKnownRequest = mock(SimpleHttpRequest.class);
            OIDCConfigurationRepresentation oidcConfig = new OIDCConfigurationRepresentation();
            // jwksUri is null by default

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

            kubeUtilsMock.when(KubernetesUtils::readServiceAccountToken).thenReturn(SA_TOKEN);

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
}
