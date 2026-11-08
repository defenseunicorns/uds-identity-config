/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public interface RealmMigration {
    String version();

    void migrate(KeycloakSession session, RealmModel realm);
}
