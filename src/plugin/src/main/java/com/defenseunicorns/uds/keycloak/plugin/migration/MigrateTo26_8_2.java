/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import org.keycloak.migration.MigrationProvider;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ProtocolMapperDelegationSyncer;
import org.keycloak.models.Constants;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.util.List;

/** Applies the 26.8.2 Admin API scope assignments and mapper delegation settings. */
public final class MigrateTo26_8_2 {
    static final String MARKER = "uds.keycloak.migration.26.8.2";
    static final String SCOPE_NAME = "kc-admin-api";
    private static final List<String> ADMIN_CLIENTS = List.of(
            "uds-operator", "uds-fleet-admin", "uds-opentofu-client");

    /** Adds the Admin API scope to UDS admin clients and reconciles configured mapper types. */
    public void migrate(KeycloakSession session, RealmModel realm) {
        ClientScopeModel scope = session.getProvider(MigrationProvider.class).addOIDCAdminApiClientScope(realm);

        for (String clientId : ADMIN_CLIENTS) {
            ClientModel client = realm.getClientByClientId(clientId);
            if (client != null && !client.getClientScopes(true).containsKey(SCOPE_NAME)) {
                client.addClientScope(scope, true);
            }
        }
        ProtocolMapperDelegationSyncer.sync(session, realm, realm.getAttribute(Constants.CLIENT_PROFILES));
    }
}
