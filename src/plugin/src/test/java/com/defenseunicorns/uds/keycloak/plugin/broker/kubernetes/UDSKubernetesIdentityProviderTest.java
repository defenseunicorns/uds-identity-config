/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.broker.kubernetes.KubernetesIdentityProviderConfig;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UDSKubernetesIdentityProviderTest {

    private static final String K8S_ISSUER = "https://kubernetes.default.svc.cluster.local";
    private static final String EKS_ISSUER = "https://oidc.eks.us-gov-west-1.amazonaws.com/id/ABC123";

    @Mock private KeycloakSession session;

    private UDSKubernetesIdentityProvider provider;

    @BeforeEach
    void setup() {
        UDSKubernetesIdentityProvider.clearIssuerCache();

        IdentityProviderModel model = new IdentityProviderModel();
        model.getConfig().put("issuer", K8S_ISSUER);
        KubernetesIdentityProviderConfig config = new KubernetesIdentityProviderConfig(model);

        provider = new UDSKubernetesIdentityProvider(session, config);
    }

    @AfterEach
    void tearDown() {
        UDSKubernetesIdentityProvider.clearIssuerCache();
    }

    @Test
    void testDiscoverIssuerReturnsDiscoveredIssuer() throws Exception {
        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class)) {
            setupMockOidcDiscovery(simpleHttpMock, "{\"issuer\":\"" + EKS_ISSUER + "\"}");

            String result = provider.discoverIssuer(K8S_ISSUER);

            assertEquals(EKS_ISSUER, result);
        }
    }

    @Test
    void testDiscoverIssuerCachesResult() throws Exception {
        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class)) {
            SimpleHttp mockHttp = setupMockOidcDiscovery(simpleHttpMock, "{\"issuer\":\"" + EKS_ISSUER + "\"}");

            String result1 = provider.discoverIssuer(K8S_ISSUER);
            assertEquals(EKS_ISSUER, result1);

            // Second call should use cache -- no additional HTTP call
            String result2 = provider.discoverIssuer(K8S_ISSUER);
            assertEquals(EKS_ISSUER, result2);

            verify(mockHttp, times(1)).doGet(anyString());
        }
    }

    @Test
    void testDiscoverIssuerFallsBackOnFailure() {
        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class)) {
            SimpleHttp mockHttp = mock(SimpleHttp.class);
            simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);
            when(mockHttp.doGet(anyString())).thenThrow(new RuntimeException("Connection refused"));

            String result = provider.discoverIssuer(K8S_ISSUER);

            assertEquals(K8S_ISSUER, result);
        }
    }

    @Test
    void testDiscoverIssuerRejectsNonHttpsIssuer() throws Exception {
        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class)) {
            setupMockOidcDiscovery(simpleHttpMock, "{\"issuer\":\"http://insecure-issuer.example.com\"}");

            String result = provider.discoverIssuer(K8S_ISSUER);

            assertEquals(K8S_ISSUER, result);
        }
    }

    @Test
    void testDiscoverIssuerRejectsEmptyIssuer() throws Exception {
        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class)) {
            setupMockOidcDiscovery(simpleHttpMock, "{\"issuer\":\"\"}");

            String result = provider.discoverIssuer(K8S_ISSUER);

            assertEquals(K8S_ISSUER, result);
        }
    }

    @Test
    void testDiscoverIssuerRejectsNullIssuer() throws Exception {
        try (MockedStatic<SimpleHttp> simpleHttpMock = mockStatic(SimpleHttp.class)) {
            setupMockOidcDiscovery(simpleHttpMock, "{\"jwks_uri\":\"https://example.com/keys\"}");

            String result = provider.discoverIssuer(K8S_ISSUER);

            assertEquals(K8S_ISSUER, result);
        }
    }

    /**
     * Sets up mock SimpleHttp to return a raw JSON string from the OIDC discovery endpoint.
     * The discoverIssuer method uses asString() + JsonSerialization to parse the response,
     * so we mock the string response rather than asJson().
     */
    private SimpleHttp setupMockOidcDiscovery(MockedStatic<SimpleHttp> simpleHttpMock, String responseBody) throws Exception {
        SimpleHttp mockHttp = mock(SimpleHttp.class);
        SimpleHttpRequest mockRequest = mock(SimpleHttpRequest.class);

        simpleHttpMock.when(() -> SimpleHttp.create(session)).thenReturn(mockHttp);
        when(mockHttp.doGet(anyString())).thenReturn(mockRequest);
        when(mockRequest.acceptJson()).thenReturn(mockRequest);
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        when(mockRequest.asString()).thenReturn(responseBody);

        return mockHttp;
    }
}
