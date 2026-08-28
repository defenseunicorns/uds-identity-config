/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.hostname;

import java.net.URI;

import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.common.util.Environment;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.HostnameProviderFactory;

/**
 * Creates the UDS hostname provider with the standard Keycloak hostname configuration.
 */
public final class UDSHostnameProviderFactory implements HostnameProviderFactory, EnvironmentDependentProviderFactory {
    private static final String DEFAULT_CLUSTER_DOMAIN = "cluster.local";
    private static final String UDS_CLUSTER_DOMAIN_ENV = "UDS_CLUSTER_DOMAIN";

    private String hostname;
    private URI hostnameUrl;
    private URI adminUrl;
    private Boolean backchannelDynamic;
    private String clusterDomain;

    @Override
    public void init(Config.Scope config) {
        if (Environment.isNonServerMode()) {
            return;
        }

        String configuredHostname = config.get("hostname");
        boolean hostnameStrict = config.getBoolean("hostname-strict", false);
        if (hostnameStrict && configuredHostname == null) {
            throw new IllegalArgumentException(
                    "hostname is not configured; either configure hostname, or set hostname-strict to false"
            );
        }

        if (configuredHostname != null) {
            if (configuredHostname.startsWith("http://") || configuredHostname.startsWith("https://")) {
                hostnameUrl = toUri(configuredHostname);
            } else {
                hostname = validateHostname(configuredHostname);
            }
        }

        adminUrl = toUri(config.get("hostname-admin"));
        if (adminUrl != null && hostnameUrl == null) {
            throw new IllegalArgumentException("hostname must be set to a URL when hostname-admin is set");
        }

        backchannelDynamic = config.getBoolean("hostname-backchannel-dynamic", false);
        if (hostname == null && hostnameUrl == null && backchannelDynamic) {
            throw new IllegalArgumentException(
                    "hostname-backchannel-dynamic must be set to false when no hostname is provided"
            );
        }
        if (backchannelDynamic && hostnameUrl == null) {
            throw new IllegalArgumentException(
                    "hostname-backchannel-dynamic must be set to false if hostname is not provided as full URL"
            );
        }

        clusterDomain = System.getenv().getOrDefault(UDS_CLUSTER_DOMAIN_ENV, DEFAULT_CLUSTER_DOMAIN);
    }

    @Override
    public HostnameProvider create(KeycloakSession session) {
        return new UDSHostnameProvider(session, hostname, hostnameUrl, adminUrl, backchannelDynamic, clusterDomain);
    }

    private static URI toUri(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.endsWith("/") ? value : value + "/";
        URI uri = URI.create(normalizedValue);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Provided hostname is not a valid URL: " + value);
        }
        return uri;
    }

    private static String validateHostname(String value) {
        URI uri = URI.create("https://" + value);
        if (!value.equals(uri.getHost())) {
            throw new IllegalArgumentException("Provided hostname is neither a plain hostname nor a valid URL");
        }
        return value;
    }

    @Override
    public String getId() {
        return "v2";
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return Profile.isFeatureEnabled(Profile.Feature.HOSTNAME_V2);
    }

    @Override
    public int order() {
        return 1;
    }
}
