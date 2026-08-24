/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.hostname;

import java.net.URI;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.url.HostnameV2ProviderFactory;
import org.keycloak.urls.HostnameProvider;

/**
 * Replaces Keycloak's hostname-v2 factory so the stock hostname configuration remains authoritative.
 */
public final class UDSHostnameProviderFactory extends HostnameV2ProviderFactory {
    private String hostname;
    private URI hostnameUrl;
    private URI adminUrl;
    private Boolean backchannelDynamic;

    @Override
    public void init(Config.Scope config) {
        super.init(config);

        String configuredHostname = config.get("hostname");
        if (configuredHostname != null) {
            if (configuredHostname.startsWith("http://") || configuredHostname.startsWith("https://")) {
                hostnameUrl = toUri(configuredHostname);
            } else {
                hostname = configuredHostname;
            }
        }

        adminUrl = toUri(config.get("hostname-admin"));
        backchannelDynamic = config.getBoolean("hostname-backchannel-dynamic", false);
    }

    @Override
    public HostnameProvider create(KeycloakSession session) {
        return new UDSHostnameProvider(session, hostname, hostnameUrl, adminUrl, backchannelDynamic);
    }

    private static URI toUri(String value) {
        return value == null ? null : URI.create(value.endsWith("/") ? value : value + "/");
    }

    @Override
    public String getId() {
        return "v2";
    }

    @Override
    public int order() {
        return 1;
    }
}
