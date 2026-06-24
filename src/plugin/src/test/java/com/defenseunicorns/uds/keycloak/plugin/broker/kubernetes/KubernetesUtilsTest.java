/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.junit.jupiter.api.Test;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.models.KeycloakSession;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the trusted-API-URL allowlist and the {@link KubernetesUtils#fetchJson} fetch. The pod service-account
 * token is attached only when the caller passes {@code attachToken=true} (i.e. the destination is the in-cluster
 * API server), so it is never sent to external/public discovery or JWKS endpoints.
 */
class KubernetesUtilsTest {

    private static final String IN_CLUSTER = "https://kubernetes.default.svc.cluster.local";
    private static final String ACCEPT = "application/json";
    private static final String TOKEN = "pod-sa-token";

    private record Body(String value) {
    }

    private static final Body BODY = new Body("ok");

    // ---- isTrustedKubernetesApiUrl ----

    @Test
    void trustsInClusterApiDnsNames() {
        assertTrue(KubernetesUtils.isTrustedKubernetesApiUrl("https://kubernetes"));
        assertTrue(KubernetesUtils.isTrustedKubernetesApiUrl("https://kubernetes.default"));
        assertTrue(KubernetesUtils.isTrustedKubernetesApiUrl("https://kubernetes.default.svc"));
        assertTrue(KubernetesUtils.isTrustedKubernetesApiUrl(IN_CLUSTER));
        assertTrue(KubernetesUtils.isTrustedKubernetesApiUrl(IN_CLUSTER + ":443"));
    }

    @Test
    void rejectsExternalHostsAndNonHttps() {
        assertFalse(KubernetesUtils.isTrustedKubernetesApiUrl("https://oidc.eks.us-gov-west-1.amazonaws.com/id/ABC123"));
        assertFalse(KubernetesUtils.isTrustedKubernetesApiUrl("https://example.com"));
        // non-HTTPS in-cluster name is still rejected
        assertFalse(KubernetesUtils.isTrustedKubernetesApiUrl("http://kubernetes.default.svc.cluster.local"));
        // wrong explicit port (no KUBERNETES_SERVICE_PORT_HTTPS env in the test → must be 443)
        assertFalse(KubernetesUtils.isTrustedKubernetesApiUrl(IN_CLUSTER + ":8443"));
    }

    // ---- isTrustedKubernetesApiJwksUrl ----

    @Test
    void trustsJwksThatIsItselfTheApiServer() {
        assertTrue(KubernetesUtils.isTrustedKubernetesApiJwksUrl(IN_CLUSTER + "/openid/v1/jwks", IN_CLUSTER));
    }

    @Test
    void trustsInClusterIpJwksWhenDiscoveryBaseTrusted() {
        // apiserver commonly advertises its JWKS at an IP-literal /openid/v1/jwks
        assertTrue(KubernetesUtils.isTrustedKubernetesApiJwksUrl("https://10.0.0.1/openid/v1/jwks", IN_CLUSTER));
    }

    @Test
    void rejectsExternalJwksWhenBaseNotTrusted() {
        assertFalse(KubernetesUtils.isTrustedKubernetesApiJwksUrl(
                "https://s3.amazonaws.com/eks/keys.json", "https://oidc.eks.us-gov-west-1.amazonaws.com/id/ABC123"));
    }

    @Test
    void rejectsNonJwksPathEvenWhenBaseTrusted() {
        assertFalse(KubernetesUtils.isTrustedKubernetesApiJwksUrl("https://10.0.0.1/evil", IN_CLUSTER));
    }

    // ---- fetchJson ----

    private SimpleHttpRequest stubHttp(MockedStatic<SimpleHttp> http, int status) throws IOException {
        SimpleHttp simpleHttp = mock(SimpleHttp.class);
        SimpleHttpRequest request = mock(SimpleHttpRequest.class);
        SimpleHttpResponse response = mock(SimpleHttpResponse.class);
        http.when(() -> SimpleHttp.create(any(KeycloakSession.class))).thenReturn(simpleHttp);
        when(simpleHttp.doGet(anyString())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.auth(anyString())).thenReturn(request);
        when(request.asResponse()).thenReturn(response);
        when(response.getStatus()).thenReturn(status);
        when(response.asJson(Body.class)).thenReturn(BODY);
        return request;
    }

    @Test
    void attachesTokenWhenTokenProvided() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class)) {
            SimpleHttpRequest request = stubHttp(http, 200);

            Body result = KubernetesUtils.fetchJson(session, IN_CLUSTER, ACCEPT, Body.class, TOKEN);

            assertEquals(BODY, result);
            verify(request, times(1)).auth(TOKEN);
        }
    }

    @Test
    void neverAttachesTokenWhenTokenNull() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class)) {
            SimpleHttpRequest request = stubHttp(http, 200);

            Body result = KubernetesUtils.fetchJson(session, "https://oidc.example/jwks", ACCEPT, Body.class, null);

            assertEquals(BODY, result);
            verify(request, never()).auth(anyString());
        }
    }

    @Test
    void rejectsNonHttpsUrlWithoutMakingRequest() {
        KeycloakSession session = mock(KeycloakSession.class);
        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class)) {
            assertThrows(IOException.class,
                    () -> KubernetesUtils.fetchJson(session, "http://issuer.example/jwks", ACCEPT, Body.class, null));
            http.verify(() -> SimpleHttp.create(any(KeycloakSession.class)), never());
        }
    }

    @Test
    void nonSuccessStatusThrows() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class)) {
            stubHttp(http, 404);
            assertThrows(IOException.class,
                    () -> KubernetesUtils.fetchJson(session, IN_CLUSTER, ACCEPT, Body.class, null));
        }
    }
}
