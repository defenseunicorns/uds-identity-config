/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.hostname;

import java.net.URI;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import org.keycloak.models.KeycloakSession;
import org.keycloak.url.HostnameV2Provider;
import org.keycloak.urls.UrlType;

/**
 * Keeps Keycloak's realm frontend URL behavior while preserving request-aware
 * origins for admin discovery and in-cluster service requests.
 */
public final class UDSHostnameProvider extends HostnameV2Provider {
    private static final String DEFAULT_CLUSTER_DOMAIN = "cluster.local";
    private static final String KUBERNETES_SERVICE_HOST_SEGMENT = ".svc";
    private static final String EXECUTE_ACTIONS_EMAIL_PATH = "/execute-actions-email";
    private static final String RESET_PASSWORD_EMAIL_PATH = "/reset-password-email";

    private final URI adminUrl;
    private final String kubernetesServiceHostSuffix;

    public UDSHostnameProvider(
            KeycloakSession session,
            String hostname,
            URI hostnameUrl,
            URI adminUrl,
            Boolean backchannelDynamic,
            String clusterDomain
    ) {
        super(session, hostname, hostnameUrl, adminUrl, backchannelDynamic);
        this.adminUrl = adminUrl;
        this.kubernetesServiceHostSuffix = KUBERNETES_SERVICE_HOST_SEGMENT + "."
                + normalizeClusterDomain(clusterDomain);
    }

    @Override
    public URI getBaseUri(UriInfo originalUriInfo, UrlType type) {
        URI baseUri = super.getBaseUri(originalUriInfo, type);

        if (type != UrlType.FRONTEND) {
            return baseUri;
        }

        if (isAdminRequest(originalUriInfo) && !isActionEmailRequest(originalUriInfo)) {
            // Admin discovery and console URLs must remain same-origin with the admin gateway.
            return withOrigin(baseUri, adminUrl);
        }

        if (isKubernetesServiceRequest(originalUriInfo)) {
            // Internal callers must not be redirected through an external gateway.
            return originalUriInfo.getBaseUri();
        }

        // Public requests and admin-triggered action emails use the realm frontend URL.
        return baseUri;
    }

    private boolean isActionEmailRequest(UriInfo originalUriInfo) {
        URI requestUri = originalUriInfo.getRequestUri();
        String path = requestUri == null ? null : requestUri.getPath();
        return path != null
                && (path.endsWith(EXECUTE_ACTIONS_EMAIL_PATH) || path.endsWith(RESET_PASSWORD_EMAIL_PATH));
    }

    private static URI withOrigin(URI baseUri, URI origin) {
        return UriBuilder.fromUri(baseUri)
                .scheme(origin.getScheme())
                .userInfo(null)
                .host(origin.getHost())
                .port(normalizedPort(origin))
                .build();
    }

    private boolean isAdminRequest(UriInfo originalUriInfo) {
        if (adminUrl == null) {
            return false;
        }

        URI requestUri = originalUriInfo.getBaseUri();
        return adminUrl.getScheme().equalsIgnoreCase(requestUri.getScheme())
                && adminUrl.getHost().equalsIgnoreCase(requestUri.getHost())
                && normalizedPort(adminUrl) == normalizedPort(requestUri);
    }

    private boolean isKubernetesServiceRequest(UriInfo originalUriInfo) {
        String host = originalUriInfo.getBaseUri().getHost();
        return host != null
                && (host.endsWith(KUBERNETES_SERVICE_HOST_SEGMENT)
                        || host.endsWith(kubernetesServiceHostSuffix));
    }

    private static String normalizeClusterDomain(String clusterDomain) {
        if (clusterDomain == null || clusterDomain.isBlank()) {
            return DEFAULT_CLUSTER_DOMAIN;
        }

        String normalized = clusterDomain.trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static int normalizedPort(URI uri) {
        if (("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 443)) {
            return -1;
        }
        return uri.getPort();
    }
}
