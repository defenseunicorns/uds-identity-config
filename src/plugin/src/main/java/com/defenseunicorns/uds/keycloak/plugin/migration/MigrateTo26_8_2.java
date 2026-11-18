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

import static com.defenseunicorns.uds.keycloak.plugin.UDSConstants.ADMIN_API_SCOPE;
import static com.defenseunicorns.uds.keycloak.plugin.UDSConstants.UDS_FLEET_ADMIN_CLIENT_ID;
import static com.defenseunicorns.uds.keycloak.plugin.UDSConstants.UDS_OPENTOFU_CLIENT_ID;
import static com.defenseunicorns.uds.keycloak.plugin.UDSConstants.UDS_OPERATOR_CLIENT_ID;

/** Applies the 26.8.2 Admin API scope assignments and mapper delegation settings. */
public final class MigrateTo26_8_2 implements RealmMigration {
    private static final List<String> ADMIN_CLIENTS = List.of(
            UDS_OPERATOR_CLIENT_ID, UDS_FLEET_ADMIN_CLIENT_ID, UDS_OPENTOFU_CLIENT_ID);

    /** {@inheritDoc} */
    @Override
    public String version() {
        return "26.8.2";
    }

    /** Waits for Keycloak's upstream Admin API scope migration method. */
    @Override
    public boolean isSupported() {
        try {
            MigrationProvider.class.getMethod("addOIDCAdminApiClientScope", RealmModel.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Adds the Admin API scope to UDS admin clients and reconciles configured mapper types. */
    @Override
    public void migrate(KeycloakSession session, RealmModel realm) {
        ClientScopeModel scope = session.getProvider(MigrationProvider.class).addOIDCAdminApiClientScope(realm);

        for (String clientId : ADMIN_CLIENTS) {
            ClientModel client = realm.getClientByClientId(clientId);
            if (client != null && !client.getClientScopes(true).containsKey(ADMIN_API_SCOPE)) {
                client.addClientScope(scope, true);
            }
        }
        ProtocolMapperDelegationSyncer.sync(session, realm, realm.getAttribute(Constants.CLIENT_PROFILES));
    }
}
