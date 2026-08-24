/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.hostname;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import jakarta.ws.rs.core.UriInfo;

import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.UrlType;

/**
 * Keeps Keycloak's hostname-v2 behavior while making admin-console frontend URLs same-origin with the admin gateway.
 * Public requests continue to use the configured frontend origin.
 */
public final class UDSHostnameProvider implements HostnameProvider {
    private static final String KUBERNETES_SERVICE_HOST_SUFFIX = ".svc.cluster.local";

    private final HostnameProvider delegate;
    private final URI adminUrl;

    public UDSHostnameProvider(HostnameProvider delegate, URI adminUrl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.adminUrl = adminUrl;
    }

    @Override
    public URI getBaseUri(UriInfo originalUriInfo, UrlType type) {
        URI baseUri = delegate.getBaseUri(originalUriInfo, type);

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

    @Override
    public String getScheme(UriInfo originalUriInfo, UrlType type) {
        return getBaseUri(originalUriInfo, type).getScheme();
    }

    @Override
    public String getScheme(UriInfo originalUriInfo) {
        return getScheme(originalUriInfo, UrlType.FRONTEND);
    }

    @Override
    public String getHostname(UriInfo originalUriInfo, UrlType type) {
        return getBaseUri(originalUriInfo, type).getHost();
    }

    @Override
    public String getHostname(UriInfo originalUriInfo) {
        return getHostname(originalUriInfo, UrlType.FRONTEND);
    }

    @Override
    public int getPort(UriInfo originalUriInfo, UrlType type) {
        return getBaseUri(originalUriInfo, type).getPort();
    }

    @Override
    public int getPort(UriInfo originalUriInfo) {
        return getPort(originalUriInfo, UrlType.FRONTEND);
    }

    @Override
    public String getContextPath(UriInfo originalUriInfo, UrlType type) {
        return getBaseUri(originalUriInfo, type).getPath();
    }

    @Override
    public String getContextPath(UriInfo originalUriInfo) {
        return getContextPath(originalUriInfo, UrlType.FRONTEND);
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
        return host != null && host.endsWith(KUBERNETES_SERVICE_HOST_SUFFIX);
    }

    private static int normalizedPort(URI uri) {
        if (("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 443)) {
            return -1;
        }
        return uri.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
