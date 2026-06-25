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
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helpers for the UDS Kubernetes identity provider. The pod service-account token is attached to an outbound
 * fetch only when the destination is the in-cluster Kubernetes API server (see {@link #isTrustedKubernetesApiUrl}),
 * so it is never sent to external/public discovery or JWKS endpoints (EKS/AKS OIDC, RKE2 S3).
 *
 * <p><b>WORKAROUND:</b> part of the temporary bridge plugin; remove once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> is resolved.
 */
final class KubernetesUtils {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String WELL_KNOWN_SUFFIX = "/.well-known/openid-configuration";

    /** System property overriding the service-account token path; defaults to the in-pod mount. */
    static final String SERVICE_ACCOUNT_TOKEN_PATH_PROPERTY = "keycloak.kubernetes.service-account-token-path";

    private KubernetesUtils() {
    }

    /**
     * GET {@code url} and parse the JSON body as {@code type}, attaching {@code token} as a bearer credential when
     * it is non-null (the caller decides, gating on {@link #isTrustedKubernetesApiUrl}). Throws if the response is
     * not 2xx, so an error body is never parsed into an empty result.
     *
     * <p>The url MUST be HTTPS. Combined with attaching the token only to trusted in-cluster URLs, this guarantees
     * the token is never sent over cleartext. Redirects can't leak it either: Keycloak's shared HttpClient disables
     * redirect handling by default, so a 3xx surfaces as a non-2xx and throws.
     */
    static <T> T fetchJson(KeycloakSession session, String url, String acceptHeader, Class<T> type,
                           String token) throws IOException {
        if (url == null || !url.startsWith("https://")) {
            throw new IOException("Refusing to fetch non-HTTPS URL (would risk exposing the pod service-account token): " + url);
        }

        SimpleHttpRequest request = SimpleHttp.create(session).doGet(url).header(HttpHeaders.ACCEPT, acceptHeader);
        if (token != null) {
            request.auth(token);
        }
        try (SimpleHttpResponse response = request.asResponse()) {
            int status = response.getStatus();
            if (status < 200 || status >= 300) {
                throw new IOException("GET " + url + " returned HTTP " + status);
            }
            return response.asJson(type);
        }
    }

    /**
     * Whether {@code url} is the in-cluster Kubernetes API server — the only destination the pod service-account
     * token may be sent to. Trusts the well-known in-cluster DNS names and the {@code KUBERNETES_SERVICE_HOST}
     * address Kubernetes injects into every pod, on 443 / {@code KUBERNETES_SERVICE_PORT_HTTPS}. No admin
     * configuration required.
     */
    static boolean isTrustedKubernetesApiUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equals(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        if ("kubernetes".equals(host) || "kubernetes.default".equals(host)
                || "kubernetes.default.svc".equals(host) || "kubernetes.default.svc.cluster.local".equals(host)) {
            return isTrustedKubernetesApiPort(uri);
        }
        String serviceHost = System.getenv(KubernetesConstants.KUBERNETES_SERVICE_HOST_KEY);
        if (serviceHost == null || !host.equals(serviceHost)) {
            return false;
        }
        return isTrustedKubernetesApiPort(uri);
    }

    /**
     * Whether the discovered {@code jwksUrl} may receive the token: either it is itself a trusted API URL, or the
     * {@code trustedBaseUrl} (the discovery source) is trusted and the JWKS is the in-cluster apiserver JWKS path
     * served at an IP-literal host ({@code https://<ip>/openid/v1/jwks}).
     */
    static boolean isTrustedKubernetesApiJwksUrl(String jwksUrl, String trustedBaseUrl) {
        if (isTrustedKubernetesApiUrl(jwksUrl)) {
            return true;
        }
        if (!isTrustedKubernetesApiUrl(trustedBaseUrl)) {
            return false;
        }
        URI jwksUri;
        try {
            jwksUri = URI.create(jwksUrl);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return "https".equals(jwksUri.getScheme())
                && "/openid/v1/jwks".equals(jwksUri.getPath())
                && jwksUri.getQuery() == null
                && jwksUri.getFragment() == null
                && isIpLiteral(jwksUri.getHost());
    }

    private static boolean isIpLiteral(String host) {
        if (host == null || (!host.contains(":") && !host.matches("\\d+(\\.\\d+){3}"))) {
            return false;
        }
        try {
            String address = InetAddress.getByName(host).getHostAddress();
            return host.contains(":") || address.equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTrustedKubernetesApiPort(URI uri) {
        String servicePort = System.getenv(KubernetesConstants.KUBERNETES_SERVICE_PORT_HTTPS_KEY);
        int port = uri.getPort();
        if (port == -1) {
            return true;
        }
        if (servicePort == null || servicePort.isBlank()) {
            return port == 443;
        }
        return servicePort.equals(Integer.toString(port));
    }

    /**
     * Resolve the cluster's issuer from its OIDC discovery document. Throws {@link IllegalArgumentException}
     * (chaining the underlying cause) if discovery is unreachable or the document doesn't contain a non-blank HTTPS
     * issuer, so the caller fails closed and the real cause surfaces. Matches upstream
     * <a href="https://github.com/keycloak/keycloak/pull/50224">keycloak/keycloak#50224</a>.
     */
    static String resolveIssuer(KeycloakSession session, String discoveryUrl) {
        String wellKnown = wellKnownUrl(discoveryUrl);
        try {
            String token = isTrustedKubernetesApiUrl(discoveryUrl) ? readServiceAccountToken() : null;
            OIDCConfigurationRepresentation discovery = fetchJson(session, wellKnown, "application/json",
                    OIDCConfigurationRepresentation.class, token);
            String issuer = discovery != null ? discovery.getIssuer() : null;
            if (issuer == null || issuer.isBlank() || !issuer.startsWith("https://")) {
                throw new IllegalArgumentException("Discovered issuer is missing or not HTTPS from " + wellKnown);
            }
            return issuer;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve Kubernetes issuer from " + wellKnown, e);
        }
    }

    /**
     * Read the mounted Keycloak pod service-account token, or null if it is not mounted or unreadable.
     */
    static String readServiceAccountToken() {
        String tokenPath = System.getProperty(SERVICE_ACCOUNT_TOKEN_PATH_PROPERTY, KubernetesConstants.SERVICE_ACCOUNT_TOKEN_PATH);
        Path path = Path.of(tokenPath);
        try {
            if (!Files.exists(path)) {
                return null;
            }
            String token = Files.readString(path, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? null : token;
        } catch (IOException e) {
            logger.warn("Failed to read service account token file {}", tokenPath, e);
            return null;
        }
    }

    /**
     * Build a normalized OIDC discovery URL for the given base, trimming any trailing slashes (matches upstream
     * <a href="https://github.com/keycloak/keycloak/pull/50224">keycloak/keycloak#50224</a>).
     */
    static String wellKnownUrl(String baseUrl) {
        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') {
            end--;
        }
        return baseUrl.substring(0, end) + WELL_KNOWN_SUFFIX;
    }
}
