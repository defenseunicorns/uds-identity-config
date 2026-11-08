/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public final class UDSMigrationsSpi implements Spi {
    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "uds-migrations";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return UDSMigrations.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return UDSMigrationsProviderFactory.class;
    }
}
