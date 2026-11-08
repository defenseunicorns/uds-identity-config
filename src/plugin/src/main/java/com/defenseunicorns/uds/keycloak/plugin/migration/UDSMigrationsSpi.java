/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/** Exposes the UDS migration runner through a dedicated Keycloak SPI. */
public final class UDSMigrationsSpi implements Spi {
    /** {@inheritDoc} */
    @Override
    public boolean isInternal() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "uds-migrations";
    }

    /** {@inheritDoc} */
    @Override
    public Class<? extends Provider> getProviderClass() {
        return UDSMigrations.class;
    }

    /** {@inheritDoc} */
    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return UDSMigrationsProviderFactory.class;
    }
}
