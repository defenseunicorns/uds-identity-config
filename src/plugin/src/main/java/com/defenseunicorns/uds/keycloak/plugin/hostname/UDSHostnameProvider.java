/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.hostname;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.ws.rs.core.UriInfo;

import org.keycloak.models.KeycloakSession;
import org.keycloak.url.HostnameV2Provider;
import org.keycloak.urls.UrlType;

/**
 * Keeps Keycloak's hostname-v2 behavior while making admin-console frontend URLs same-origin with the admin gateway.
 * Public requests continue to use the configured frontend origin.
 */
public final class UDSHostnameProvider extends HostnameV2Provider {
    private static final String KUBERNETES_SERVICE_HOST_SEGMENT = ".svc";

    private final URI adminUrl;

    public UDSHostnameProvider(
            KeycloakSession session,
            String hostname,
            URI hostnameUrl,
            URI adminUrl,
            Boolean backchannelDynamic
    ) {
        super(session, hostname, hostnameUrl, adminUrl, backchannelDynamic);
        this.adminUrl = adminUrl;
    }

    @Override
    public URI getBaseUri(UriInfo originalUriInfo, UrlType type) {
        URI baseUri = super.getBaseUri(originalUriInfo, type);

        if (type != UrlType.FRONTEND) {
            return baseUri;
        }

        if (isAdminRequest(originalUriInfo)) {
            return withOrigin(baseUri, adminUrl);
        }

        if (isKubernetesServiceRequest(originalUriInfo)) {
            return originalUriInfo.getBaseUri();
        }

        return baseUri;
    }

    private URI withOrigin(URI baseUri, URI origin) {

        try {
            URI adminOrigin = new URI(
                    origin.getScheme(),
                    null,
                    origin.getHost(),
                    normalizedPort(origin),
                    null,
                    null,
                    null
            );
            StringBuilder adminUri = new StringBuilder(adminOrigin.toString());

            if (baseUri.getRawPath() != null) {
                adminUri.append(baseUri.getRawPath());
            }
            if (baseUri.getRawQuery() != null) {
                adminUri.append('?').append(baseUri.getRawQuery());
            }
            if (baseUri.getRawFragment() != null) {
                adminUri.append('#').append(baseUri.getRawFragment());
            }

            return new URI(adminUri.toString());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to construct the admin frontend URL", e);
        }
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
                        || host.contains(KUBERNETES_SERVICE_HOST_SEGMENT + "."));
    }

    private static int normalizedPort(URI uri) {
        if (("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 443)) {
            return -1;
        }
        return uri.getPort();
    }

}
