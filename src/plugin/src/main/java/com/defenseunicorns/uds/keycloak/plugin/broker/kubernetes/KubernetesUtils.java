/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.apache.http.HttpHeaders;
import org.keycloak.broker.kubernetes.KubernetesConstants;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.models.KeycloakSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helpers for the UDS Kubernetes identity provider. Reading the pod service-account token here carries no
 * issuer-match forwarding decision; whether the token is attached to a given request is decided by the
 * anonymous-first probe in {@link #fetchJson} per {@link UDSKubernetesHttpAuthPolicy}.
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> part of the temporary bridge plugin; remove once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> is resolved.
 */
final class KubernetesUtils {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String WELL_KNOWN_SUFFIX = "/.well-known/openid-configuration";

    private KubernetesUtils() {
    }

    /**
     * GET {@code url} and parse the JSON body as {@code type}, applying the token-forwarding {@code mode}:
     * AUTO sends anonymously then retries with the pod token on a 401/403 challenge; ALWAYS sends the token up
     * front; NEVER stays anonymous. Throws if the (final) response is not 2xx, so an error body is never parsed
     * into an empty result.
     *
     * <p>The url MUST be HTTPS. This is the single chokepoint for every outbound fetch (OIDC discovery and the
     * discovered {@code jwks_uri}), so requiring TLS here keeps the pod service-account token off cleartext
     * regardless of how the URL was derived. Redirects can't leak the token either: Keycloak's shared HttpClient
     * disables redirect handling by default, so a 3xx surfaces as a non-2xx and throws.
     */
    static <T> T fetchJson(KeycloakSession session, String url, String acceptHeader, Class<T> type,
                           UDSKubernetesHttpAuthPolicy.Mode mode) throws IOException {
        if (url == null || !url.startsWith("https://")) {
            throw new IOException("Refusing to fetch non-HTTPS URL (would risk exposing the pod service-account token): " + url);
        }

        String token = (mode == UDSKubernetesHttpAuthPolicy.Mode.NEVER) ? null : readServiceAccountToken();

        // In ALWAYS mode the token is mandatory; fail fast rather than degrading to an anonymous request that
        // would surface later as an opaque 401/403 and mask the real cause (token unreadable/unmounted).
        if (mode == UDSKubernetesHttpAuthPolicy.Mode.ALWAYS && token == null) {
            throw new IOException("Token-forwarding mode is ALWAYS but the pod service account token at "
                    + KubernetesConstants.SERVICE_ACCOUNT_TOKEN_PATH + " is missing or unreadable");
        }

        SimpleHttpResponse response = get(session, url, acceptHeader, UDSKubernetesHttpAuthPolicy.firstAttemptToken(mode, token));
        try {
            int status = response.getStatus();
            if (UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(mode, status, token)) {
                response.close();
                response = get(session, url, acceptHeader, token);
                status = response.getStatus();
            }
            if (status < 200 || status >= 300) {
                throw new IOException("GET " + url + " returned HTTP " + status);
            }
            return response.asJson(type);
        } finally {
            response.close();
        }
    }

    private static SimpleHttpResponse get(KeycloakSession session, String url, String acceptHeader, String token) throws IOException {
        SimpleHttpRequest request = SimpleHttp.create(session).doGet(url)
                .header(HttpHeaders.ACCEPT, acceptHeader);
        if (token != null) {
            request.auth(token);
        }
        return request.asResponse();
    }

    /**
     * Read the mounted Keycloak pod service-account token, or null if it is not mounted or unreadable.
     */
    static String readServiceAccountToken() {
        Path path = Path.of(KubernetesConstants.SERVICE_ACCOUNT_TOKEN_PATH);
        try {
            if (!Files.exists(path)) {
                return null;
            }
            String token = Files.readString(path, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? null : token;
        } catch (IOException e) {
            logger.warn("Failed to read service account token file {}", KubernetesConstants.SERVICE_ACCOUNT_TOKEN_PATH, e);
            return null;
        }
    }

    /**
     * Build a normalized OIDC discovery URL for the given base (no double slashes).
     */
    static String wellKnownUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + WELL_KNOWN_SUFFIX;
    }
}
