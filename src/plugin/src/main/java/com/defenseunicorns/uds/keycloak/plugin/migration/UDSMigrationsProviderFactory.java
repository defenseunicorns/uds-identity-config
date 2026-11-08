/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderFactory;

public final class UDSMigrationsProviderFactory implements ProviderFactory<UDSMigrations> {
    @Override
    public UDSMigrations create(KeycloakSession session) {
        return new UDSMigrations(session);
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(event -> {
            if (event instanceof PostMigrationEvent postMigrationEvent) {
                try (KeycloakSession session = postMigrationEvent.getFactory().create()) {
                    session.getProvider(UDSMigrations.class).run();
                }
            }
        });
    }

    @Override
    public void close() {}

    @Override
    public String getId() {
        return "default";
    }
}
