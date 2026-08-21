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
    private URI adminUrl;

    @Override
    public void init(Config.Scope config) {
        super.init(config);

        String configuredAdminUrl = config.get("hostname-admin");
        if (configuredAdminUrl != null) {
            adminUrl = URI.create(configuredAdminUrl.endsWith("/") ? configuredAdminUrl : configuredAdminUrl + "/");
        }
    }

    @Override
    public HostnameProvider create(KeycloakSession session) {
        return new UDSHostnameProvider(super.create(session), adminUrl);
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
