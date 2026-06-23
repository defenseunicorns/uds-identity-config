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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the token-forwarding orchestration in {@link KubernetesUtils#fetchJson} — the core security guarantee of
 * the plugin: the Keycloak pod service-account token must never be sent to a public issuer endpoint (S3/EKS/AKS),
 * only attached when an in-cluster endpoint actually challenges with a 401/403. The per-mode decisions are unit
 * tested in {@link UDSKubernetesHttpAuthPolicyTest}; these tests verify they are wired together correctly.
 */
class KubernetesUtilsTest {

    private static final String URL = "https://issuer.example/.well-known/openid-configuration";
    private static final String ACCEPT = "application/json";
    private static final String TOKEN = "pod-sa-token";

    private record Body(String value) {
    }

    private static final Body BODY = new Body("ok");

    /**
     * Wire up the static SimpleHttp fluent chain so that successive {@code asResponse()} calls return the supplied
     * responses in order. Returns the request mock so callers can verify whether/when {@code auth()} was invoked.
     */
    private SimpleHttpRequest stubHttp(MockedStatic<SimpleHttp> http, SimpleHttpResponse... responses) throws IOException {
        SimpleHttp simpleHttp = mock(SimpleHttp.class);
        SimpleHttpRequest request = mock(SimpleHttpRequest.class);
        http.when(() -> SimpleHttp.create(any(KeycloakSession.class))).thenReturn(simpleHttp);
        when(simpleHttp.doGet(anyString())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.auth(anyString())).thenReturn(request);
        if (responses.length == 1) {
            when(request.asResponse()).thenReturn(responses[0]);
        } else {
            when(request.asResponse()).thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
        }
        return request;
    }

    private SimpleHttpResponse response(int status) throws IOException {
        SimpleHttpResponse response = mock(SimpleHttpResponse.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    @Test
    void autoSendsAnonymouslyThenRetriesWithTokenOnChallenge() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        SimpleHttpResponse challenge = response(401);
        SimpleHttpResponse ok = response(200);
        when(ok.asJson(Body.class)).thenReturn(BODY);

        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            utils.when(KubernetesUtils::readServiceAccountToken).thenReturn(TOKEN);
            SimpleHttpRequest request = stubHttp(http, challenge, ok);

            Body result = KubernetesUtils.fetchJson(session, URL, ACCEPT, Body.class, UDSKubernetesHttpAuthPolicy.Mode.AUTO);

            assertEquals(BODY, result);
            // Token attached exactly once — on the retry, never on the first anonymous attempt.
            verify(request, times(1)).auth(TOKEN);
            verify(challenge).close();
            verify(ok).close();
        }
    }

    @Test
    void autoStaysAnonymousWhenFirstAttemptSucceeds() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        SimpleHttpResponse ok = response(200);
        when(ok.asJson(Body.class)).thenReturn(BODY);

        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            utils.when(KubernetesUtils::readServiceAccountToken).thenReturn(TOKEN);
            SimpleHttpRequest request = stubHttp(http, ok);

            Body result = KubernetesUtils.fetchJson(session, URL, ACCEPT, Body.class, UDSKubernetesHttpAuthPolicy.Mode.AUTO);

            assertEquals(BODY, result);
            // A public endpoint answered 200 anonymously, so the pod token must never be sent.
            verify(request, never()).auth(anyString());
            verify(ok).close();
        }
    }

    @Test
    void neverModeNeverReadsOrAttachesToken() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        SimpleHttpResponse ok = response(200);
        when(ok.asJson(Body.class)).thenReturn(BODY);

        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            SimpleHttpRequest request = stubHttp(http, ok);

            Body result = KubernetesUtils.fetchJson(session, URL, ACCEPT, Body.class, UDSKubernetesHttpAuthPolicy.Mode.NEVER);

            assertEquals(BODY, result);
            verify(request, never()).auth(anyString());
            utils.verify(KubernetesUtils::readServiceAccountToken, never());
        }
    }

    @Test
    void alwaysModeAttachesTokenOnFirstRequest() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        SimpleHttpResponse ok = response(200);
        when(ok.asJson(Body.class)).thenReturn(BODY);

        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            utils.when(KubernetesUtils::readServiceAccountToken).thenReturn(TOKEN);
            SimpleHttpRequest request = stubHttp(http, ok);

            KubernetesUtils.fetchJson(session, URL, ACCEPT, Body.class, UDSKubernetesHttpAuthPolicy.Mode.ALWAYS);

            verify(request, times(1)).auth(TOKEN);
        }
    }

    @Test
    void alwaysModeFailsFastWhenTokenUnavailable() {
        KeycloakSession session = mock(KeycloakSession.class);

        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            utils.when(KubernetesUtils::readServiceAccountToken).thenReturn(null);

            // ALWAYS requires the token; a missing token must fail fast, not silently degrade to an anonymous request.
            assertThrows(IOException.class,
                    () -> KubernetesUtils.fetchJson(session, URL, ACCEPT, Body.class, UDSKubernetesHttpAuthPolicy.Mode.ALWAYS));
            http.verify(() -> SimpleHttp.create(any(KeycloakSession.class)), never());
        }
    }

    @Test
    void nonSuccessStatusThrowsAndClosesResponse() throws Exception {
        KeycloakSession session = mock(KeycloakSession.class);
        // 404 is not a 401/403 challenge, so AUTO does not retry; a non-2xx final response must throw rather than
        // parse an error body into an empty result.
        SimpleHttpResponse notFound = response(404);

        try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class);
             MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            utils.when(KubernetesUtils::readServiceAccountToken).thenReturn(TOKEN);
            SimpleHttpRequest request = stubHttp(http, notFound);

            assertThrows(IOException.class,
                    () -> KubernetesUtils.fetchJson(session, URL, ACCEPT, Body.class, UDSKubernetesHttpAuthPolicy.Mode.AUTO));
            verify(request, never()).auth(anyString());
            verify(notFound).close();
        }
    }
}
