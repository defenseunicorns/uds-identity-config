/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.hostname;

import java.net.URI;
import java.util.ServiceLoader;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.HostnameProviderFactory;
import org.keycloak.urls.UrlType;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class UDSHostnameProviderTest {
    private static final URI PUBLIC_URL = URI.create("https://sso.uds.dev/");
    private static final URI ADMIN_URL = URI.create("https://keycloak.admin.uds.dev/");
    private static final URI INTERNAL_URL = URI.create("http://keycloak-http.keycloak.svc.cluster.local:8080/");
    private static final URI CUSTOM_DOMAIN_INTERNAL_URL =
            URI.create("http://keycloak-http.keycloak.svc.cluster.internal:8080/");
    private static final URI EXTERNAL_SVC_URL = URI.create("https://tenant.svc.example.com/");

    @Test
    void usesAdminOriginForFrontendUrlsFromAdminGateway() {
        UDSHostnameProvider provider = provider();

        assertEquals(ADMIN_URL, provider.getBaseUri(request(ADMIN_URL, PUBLIC_URL), UrlType.FRONTEND));
        assertEquals("https", provider.getScheme(request(ADMIN_URL, PUBLIC_URL), UrlType.FRONTEND));
        assertEquals("keycloak.admin.uds.dev", provider.getHostname(request(ADMIN_URL, PUBLIC_URL), UrlType.FRONTEND));
        assertEquals(-1, provider.getPort(request(ADMIN_URL, PUBLIC_URL), UrlType.FRONTEND));
        assertEquals("/", provider.getContextPath(request(ADMIN_URL, PUBLIC_URL), UrlType.FRONTEND));
    }

    @Test
    void keepsPublicOriginForPublicFrontendRequests() {
        UDSHostnameProvider provider = provider();

        assertEquals(PUBLIC_URL, provider.getBaseUri(request(PUBLIC_URL), UrlType.FRONTEND));
    }

    @Test
    void keepsInternalOriginForKubernetesServiceRequests() {
        UDSHostnameProvider provider = provider();

        assertEquals(INTERNAL_URL, provider.getBaseUri(request(INTERNAL_URL, PUBLIC_URL), UrlType.FRONTEND));
    }

    @Test
    void keepsInternalOriginForKubernetesServiceRequestsWithCustomClusterDomain() {
        UDSHostnameProvider provider = provider(ADMIN_URL, "cluster.internal");

        assertEquals(
                CUSTOM_DOMAIN_INTERNAL_URL,
                provider.getBaseUri(request(CUSTOM_DOMAIN_INTERNAL_URL, PUBLIC_URL), UrlType.FRONTEND)
        );
    }

    @Test
    void doesNotTreatAnExternalSvcHostAsAnInternalService() {
        UDSHostnameProvider provider = provider();

        assertEquals(PUBLIC_URL, provider.getBaseUri(request(EXTERNAL_SVC_URL, PUBLIC_URL), UrlType.FRONTEND));
    }

    @Test
    void keepsStockBehaviorForOtherUrlTypes() {
        UDSHostnameProvider provider = provider();
        UriInfo request = request(ADMIN_URL, PUBLIC_URL);

        assertEquals(PUBLIC_URL, provider.getBaseUri(request, UrlType.BACKEND));
    }

    @Test
    void preservesTheStockContextPath() {
        UDSHostnameProvider provider = provider();

        assertEquals(
                URI.create("https://keycloak.admin.uds.dev/auth/"),
                provider.getBaseUri(request(ADMIN_URL, URI.create("https://sso.uds.dev/auth/")), UrlType.FRONTEND)
        );
    }

    @Test
    void preservesEncodedFrontendUrlComponents() {
        URI stockBaseUri = URI.create(
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
                provider().getBaseUri(request(ADMIN_URL, stockBaseUri), UrlType.FRONTEND)
        );
    }

    @Test
    void doesNotTreatAnUnconfiguredOriginAsTheAdminGateway() {
        UDSHostnameProvider provider = provider();
        URI untrustedUrl = URI.create("https://attacker.example/");

        assertEquals(PUBLIC_URL, provider.getBaseUri(request(untrustedUrl, PUBLIC_URL), UrlType.FRONTEND));
    }

    @Test
    void normalizesDefaultAdminPort() {
        URI configuredAdminUrl = URI.create("https://keycloak.admin.uds.dev:443/");

        assertEquals(
                ADMIN_URL,
                provider(configuredAdminUrl).getBaseUri(request(ADMIN_URL, PUBLIC_URL), UrlType.FRONTEND)
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

    @Test
    void createsProviderWithConfiguredHostnameAndBackchannelBehavior() {
        RuntimeDelegate runtimeDelegate = mock(RuntimeDelegate.class);
        UriBuilder hostnameBuilder = mock(UriBuilder.class);
        when(runtimeDelegate.createUriBuilder()).thenReturn(hostnameBuilder);
        when(hostnameBuilder.uri(PUBLIC_URL)).thenReturn(hostnameBuilder);
        when(hostnameBuilder.build()).thenReturn(PUBLIC_URL);

        try (MockedStatic<RuntimeDelegate> runtimeDelegateMock = mockStatic(RuntimeDelegate.class)) {
            runtimeDelegateMock.when(RuntimeDelegate::getInstance).thenReturn(runtimeDelegate);

            UDSHostnameProviderFactory factory = factory(PUBLIC_URL, ADMIN_URL, true);
            HostnameProvider provider = factory.create(null);

            assertInstanceOf(UDSHostnameProvider.class, provider);
            assertEquals(
                    ADMIN_URL,
                    provider.getBaseUri(request(ADMIN_URL, PUBLIC_URL), UrlType.FRONTEND)
            );
            assertEquals(
                    INTERNAL_URL,
                    provider.getBaseUri(request(INTERNAL_URL, INTERNAL_URL), UrlType.BACKEND)
            );
        }
    }

    private UDSHostnameProvider provider() {
        return provider(ADMIN_URL);
    }

    private UDSHostnameProvider provider(URI configuredAdminUrl) {
        return provider(configuredAdminUrl, "cluster.local");
    }

    private UDSHostnameProvider provider(URI configuredAdminUrl, String clusterDomain) {
        return new UDSHostnameProvider(null, null, null, configuredAdminUrl, false, clusterDomain);
    }

    private UDSHostnameProviderFactory factory(URI hostnameUrl, URI configuredAdminUrl, boolean backchannelDynamic) {
        UDSHostnameProviderFactory factory = new UDSHostnameProviderFactory();
        Config.Scope config = mock(Config.Scope.class);
        when(config.get("hostname")).thenReturn(hostnameUrl.toString());
        when(config.get("hostname-admin")).thenReturn(configuredAdminUrl.toString());
        when(config.getBoolean("hostname-strict", false)).thenReturn(false);
        when(config.getBoolean("hostname-backchannel-dynamic", false)).thenReturn(backchannelDynamic);
        factory.init(config);
        return factory;
    }

    private UriInfo request(URI baseUri) {
        return request(baseUri, baseUri);
    }

    private UriInfo request(URI baseUri, URI stockBaseUri) {
        UriInfo uriInfo = mock(UriInfo.class);
        UriBuilder baseUriBuilder = mock(UriBuilder.class);
        when(uriInfo.getBaseUri()).thenReturn(baseUri);
        when(uriInfo.getBaseUriBuilder()).thenReturn(baseUriBuilder);
        when(baseUriBuilder.build()).thenReturn(stockBaseUri);
        return uriInfo;
    }
}
