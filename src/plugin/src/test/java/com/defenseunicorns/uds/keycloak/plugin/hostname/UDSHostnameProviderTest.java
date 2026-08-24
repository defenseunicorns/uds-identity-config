/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.hostname;

import java.net.URI;
import java.util.ServiceLoader;

import jakarta.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Test;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.HostnameProviderFactory;
import org.keycloak.urls.UrlType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UDSHostnameProviderTest {
    private static final URI PUBLIC_URL = URI.create("https://sso.uds.dev/");
    private static final URI ADMIN_URL = URI.create("https://keycloak.admin.uds.dev/");
    private static final URI INTERNAL_URL = URI.create("http://keycloak-http.keycloak.svc.cluster.local:8080/");

    @Test
    void usesAdminOriginForFrontendUrlsFromAdminGateway() {
        UDSHostnameProvider provider = provider(PUBLIC_URL);

        assertEquals(ADMIN_URL, provider.getBaseUri(request(ADMIN_URL), UrlType.FRONTEND));
        assertEquals("https", provider.getScheme(request(ADMIN_URL), UrlType.FRONTEND));
        assertEquals("keycloak.admin.uds.dev", provider.getHostname(request(ADMIN_URL), UrlType.FRONTEND));
        assertEquals(-1, provider.getPort(request(ADMIN_URL), UrlType.FRONTEND));
        assertEquals("/", provider.getContextPath(request(ADMIN_URL), UrlType.FRONTEND));
    }

    @Test
    void keepsPublicOriginForPublicFrontendRequests() {
        UDSHostnameProvider provider = provider(PUBLIC_URL);

        assertEquals(PUBLIC_URL, provider.getBaseUri(request(PUBLIC_URL), UrlType.FRONTEND));
    }

    @Test
    void keepsInternalOriginForKubernetesServiceRequests() {
        UDSHostnameProvider provider = provider(PUBLIC_URL);

        assertEquals(INTERNAL_URL, provider.getBaseUri(request(INTERNAL_URL), UrlType.FRONTEND));
    }

    @Test
    void keepsDelegatedBehaviorForOtherUrlTypes() {
        UDSHostnameProvider provider = provider(PUBLIC_URL);
        UriInfo request = request(ADMIN_URL);

        assertEquals(PUBLIC_URL, provider.getBaseUri(request, UrlType.BACKEND));
    }

    @Test
    void preservesTheDelegatedContextPath() {
        UDSHostnameProvider provider = provider(URI.create("https://sso.uds.dev/auth/"));

        assertEquals(
                URI.create("https://keycloak.admin.uds.dev/auth/"),
                provider.getBaseUri(request(ADMIN_URL), UrlType.FRONTEND)
        );
    }

    @Test
    void preservesEncodedFrontendUrlComponents() {
        URI delegatedBaseUri = URI.create(
                "https://sso.uds.dev/realms/%2Fuds"
                        + "?redirect_uri=https%3A%2F%2Fapp.example%2Fcallback%3Fnext%3Da%252Fb%26x%3D1"
                        + "#frag%20ment"
        );

        assertEquals(
                URI.create(
                        "https://keycloak.admin.uds.dev/realms/%2Fuds"
                                + "?redirect_uri=https%3A%2F%2Fapp.example%2Fcallback%3Fnext%3Da%252Fb%26x%3D1"
                                + "#frag%20ment"
                ),
                provider(delegatedBaseUri).getBaseUri(request(ADMIN_URL), UrlType.FRONTEND)
        );
    }

    @Test
    void doesNotTreatAnUnconfiguredOriginAsTheAdminGateway() {
        UDSHostnameProvider provider = provider(PUBLIC_URL);
        URI untrustedUrl = URI.create("https://attacker.example/");

        assertEquals(PUBLIC_URL, provider.getBaseUri(request(untrustedUrl), UrlType.FRONTEND));
    }

    @Test
    void normalizesDefaultAdminPort() {
        URI configuredAdminUrl = URI.create("https://keycloak.admin.uds.dev:443/");

        assertEquals(
                ADMIN_URL,
                provider(PUBLIC_URL, configuredAdminUrl).getBaseUri(request(ADMIN_URL), UrlType.FRONTEND)
        );
    }

    @Test
    void registersTheHigherPriorityV2Factory() {
        UDSHostnameProviderFactory factory = ServiceLoader.load(HostnameProviderFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(UDSHostnameProviderFactory.class::isInstance)
                .map(UDSHostnameProviderFactory.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals("v2", factory.getId());
        assertEquals(1, factory.order());
    }

    private UDSHostnameProvider provider(URI delegatedBaseUri) {
        return provider(delegatedBaseUri, ADMIN_URL);
    }

    private UDSHostnameProvider provider(URI delegatedBaseUri, URI configuredAdminUrl) {
        HostnameProvider delegate = mock(HostnameProvider.class);
        when(delegate.getBaseUri(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(delegatedBaseUri);
        return new UDSHostnameProvider(delegate, configuredAdminUrl);
    }

    private UriInfo request(URI baseUri) {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(baseUri);
        return uriInfo;
    }
}
