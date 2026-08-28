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
 * Keeps Keycloak's hostname-v2 behavior while making admin-console frontend URLs same-origin with the admin gateway.
 * Public requests continue to use the configured frontend origin.
 */
public final class UDSHostnameProvider extends HostnameV2Provider {
    private static final String DEFAULT_CLUSTER_DOMAIN = "cluster.local";
    private static final String KUBERNETES_SERVICE_HOST_SEGMENT = ".svc";

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
            // Backend and admin URL types retain the stock Keycloak behavior.
            return baseUri;
        }

        if (isAdminRequest(originalUriInfo)) {
            // Requests through the admin gateway must generate frontend URLs on the admin origin.
            return withOrigin(baseUri, adminUrl);
        }

        if (isKubernetesServiceRequest(originalUriInfo)) {
            // In-cluster requests must continue using their service origin for internal callbacks.
            return originalUriInfo.getBaseUri();
        }

        // Public and tenant gateway requests use the configured public frontend origin.
        return baseUri;
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
