/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ProtocolMapperDelegation;
import org.keycloak.Config;
import org.keycloak.models.Constants;
import org.keycloak.models.RealmModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderFactory;

/** Registers migration startup and live profile synchronization independently of Client Policy execution. */
public final class UDSMigrationsProviderFactory implements ProviderFactory<UDSMigrations> {
    /** Creates the session-scoped migration runner. */
    @Override
    public UDSMigrations create(KeycloakSession session) {
        return new UDSMigrations(session);
    }

    /** No provider-specific configuration is required. */
    @Override
    public void init(Config.Scope config) {}

    /**
     * Runs startup migrations and synchronizes persisted profile edits in the updating transaction.
     * Profile updates publish a model event even when admin-event logging is disabled.
     */
    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(event -> {
            if (event instanceof RealmModel.RealmAttributeUpdateEvent update
                    && Constants.CLIENT_PROFILES.equals(update.getAttributeName())
                    && "uds".equals(update.getRealm().getName())) {
                ProtocolMapperDelegation.sync(update.getKeycloakSession(), update.getRealm(), update.getAttributeValue());
            } else if (event instanceof PostMigrationEvent postMigrationEvent) {
                try (KeycloakSession session = postMigrationEvent.getFactory().create()) {
                    session.getProvider(UDSMigrations.class).run();
                }
            }
        });
    }

    /** No factory resources require cleanup. */
    @Override
    public void close() {}

    /** Selects this factory as the default UDS migration provider. */
    @Override
    public String getId() {
        return "default";
    }
}
