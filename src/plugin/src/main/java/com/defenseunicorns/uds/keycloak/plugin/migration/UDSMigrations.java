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

/** Applies the one-time UDS realm change under Keycloak's boot database lock. */
public final class UDSMigrations implements Provider {
    private static final Logger LOG = Logger.getLogger(UDSMigrations.class);

    private final KeycloakSession session;

    /** Uses the caller session for locking and its factory for the migration transaction. */
    public UDSMigrations(KeycloakSession session) {
        this.session = session;
    }

    /**
     * Migrates only the uds realm, committing the completion marker with its changes before
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
                if (!Boolean.parseBoolean(realm.getAttribute(MigrateTo26_8_2.MARKER))) {
                    new MigrateTo26_8_2().migrate(migrationSession, realm);
                    realm.setAttribute(MigrateTo26_8_2.MARKER, true);
                    LOG.info("Applied UDS realm migration 26.8.2");
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
