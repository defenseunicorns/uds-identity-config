/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.dblock.DBLockManager;
import org.keycloak.models.dblock.DBLockProvider;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.Provider;

import java.util.List;

/** Applies ordered UDS realm changes under Keycloak's boot database lock. */
public final class UDSMigrations implements Provider {
    private static final Logger LOG = Logger.getLogger(UDSMigrations.class);
    private static final String MARKER_PREFIX = "uds.keycloak.migration.";
    // Add migrations here in version order; each marker is committed with its realm changes under the boot DB lock.
    private static final List<RealmMigration> MIGRATIONS = List.of(new MigrateTo26_8_2());

    private final KeycloakSession session;

    /** Uses the caller session for locking and its factory for the migration transaction. */
    public UDSMigrations(KeycloakSession session) {
        this.session = session;
    }

    /**
     * Migrates only the uds realm, committing each completion marker with its changes before
     * releasing the lock. Failures propagate without recording successful completion.
     */
    public void run() {
        DBLockProvider lock = new DBLockManager(session).getDBLock();
        lock.waitForLock(DBLockProvider.Namespace.KEYCLOAK_BOOT);
        try {
            KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), migrationSession -> {
                RealmModel realm = migrationSession.realms().getRealmByName("uds");
                if (realm == null) {
                    return;
                }
                for (RealmMigration migration : MIGRATIONS) {
                    String marker = MARKER_PREFIX + migration.version();
                    if (!Boolean.parseBoolean(realm.getAttribute(marker))) {
                        migration.migrate(migrationSession, realm);
                        realm.setAttribute(marker, true);
                        LOG.infof("Applied UDS realm migration %s", migration.version());
                    }
                }
            });
        } finally {
            lock.releaseLock();
        }
    }

    /** No resources are held between invocations. */
    @Override
    public void close() {}
}
