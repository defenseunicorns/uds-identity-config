/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/** A versioned, repeatable realm change executed inside the migration transaction. */
public interface RealmMigration {
    /** Returns the stable version used in the realm completion marker. */
    String version();

    /** Applies the change without committing; the runner records completion in the same transaction. */
    void migrate(KeycloakSession session, RealmModel realm);
}
