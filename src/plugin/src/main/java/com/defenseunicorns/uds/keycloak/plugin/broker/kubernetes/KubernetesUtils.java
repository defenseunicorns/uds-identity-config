/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.util.JsonSerialization;

import org.jboss.logging.Logger;

import static org.keycloak.broker.kubernetes.KubernetesConstants.SERVICE_ACCOUNT_TOKEN_PATH;

/**
 * Shared utilities for Kubernetes identity provider classes.
 */
final class KubernetesUtils {

    private static final Logger LOGGER = Logger.getLogger(KubernetesUtils.class);

    private KubernetesUtils() {
    }

    /**
     * Executes the request, checks for a 2xx status, and deserializes the response body.
     *
     * @throws IllegalStateException if the response status is not 2xx
     */
    static <T> T executeAndParse(SimpleHttpRequest request, String url, Class<T> type) throws Exception {
        try (SimpleHttpResponse response = request.asResponse()) {
            int status = response.getStatus();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Request to " + url + " returned HTTP " + status);
            }
            return JsonSerialization.readValue(response.asString(), type);
        }
    }

    /**
     * Reads the Kubernetes service account token from the standard mounted path.
     *
     * <h2>Why this exists instead of upstream {@code KubernetesJwksEndpointLoader.getToken()}</h2>
     *
     * The upstream {@code getToken()} parses the SA token and only returns it when its
     * {@code iss} claim matches the configured IdP issuer:
     * <pre>
     * if (jwt.getIssuer().equals(issuer)) {
     *     return token;
     * } else {
     *     return null;  // "issuer mismatch"
     * }
     * </pre>
     *
     * On managed Kubernetes clusters (EKS, AKS, GKE), the SA token issuer is a cloud-specific
     * URL (e.g. {@code https://oidc.eks.*.amazonaws.com/id/...}) while the configured issuer
     * is {@code https://kubernetes.default.svc.cluster.local}. These never match, so upstream
     * always excludes the token, causing 401 errors on OIDC discovery and JWKS requests.
     *
     * <p>This method returns the raw token unconditionally. The caller
     * ({@link UDSKubernetesJwksEndpointLoader}) decides whether to forward it based on the
     * OIDC discovery response's {@code issuer} field instead.
     *
     * @return the SA token string, or {@code null} if the file doesn't exist or can't be read
     * @see UDSKubernetesJwksEndpointLoader#shouldIncludeToken(String, String)
     */
    static String readServiceAccountToken() {
        try {
            var path = Paths.get(SERVICE_ACCOUNT_TOKEN_PATH);
            if (!Files.exists(path)) {
                return null;
            }
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            LOGGER.warn("Failed to read service account token", e);
            return null;
        }
    }
}
