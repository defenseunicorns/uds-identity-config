/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import com.defenseunicorns.uds.keycloak.plugin.UDSAdminApiAudienceMapper;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ProtocolMapperDelegationSyncer;
import org.keycloak.models.Constants;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;

import java.util.List;

/** Prepares existing realms for the 26.8.2 Admin API audience and mapper delegation requirements. */
public final class MigrateTo26_8_2 implements RealmMigration {
    static final String SCOPE_NAME = "kc-admin-api";
    private static final String MAPPER_NAME = "uds-admin-api-audience";
    private static final List<String> ADMIN_CLIENTS = List.of(
            "uds-operator", "uds-fleet-admin", "uds-opentofu-client", "admin-cli", "security-admin-console");

    /** {@inheritDoc} */
    @Override
    public String version() {
        return "26.8.2";
    }

    /** Adds the compatibility scope to existing admin clients and reconciles configured mapper types. */
    @Override
    public void migrate(KeycloakSession session, RealmModel realm) {
        session.getContext().setRealm(realm);
        ClientScopeModel scope = realm.getClientScopesStream()
                .filter(candidate -> SCOPE_NAME.equals(candidate.getName()))
                .findFirst()
                .orElseGet(() -> session.clientScopes().addClientScope(realm, SCOPE_NAME));
        scope.setProtocol("openid-connect");
        scope.setAttribute("include.in.token.scope", "true");
        addAudienceMapper(scope);

        for (String clientId : ADMIN_CLIENTS) {
            ClientModel client = realm.getClientByClientId(clientId);
            if (client != null && !client.getClientScopes(true).containsKey(SCOPE_NAME)) {
                client.addClientScope(scope, true);
            }
        }
        ProtocolMapperDelegationSyncer.sync(session, realm, realm.getAttribute(Constants.CLIENT_PROFILES));
    }

    /** Creates or repairs the UDS mapper without replacing a separately named native mapper. */
    private void addAudienceMapper(ClientScopeModel scope) {
        ProtocolMapperModel mapper = scope.getProtocolMappersStream()
                .filter(existing -> MAPPER_NAME.equals(existing.getName()))
                .findFirst().orElse(null);
        boolean isNew = mapper == null;
        if (isNew) {
            mapper = UDSAdminApiAudienceMapper.createMapper(MAPPER_NAME);
        } else {
            ProtocolMapperModel configured = UDSAdminApiAudienceMapper.createMapper(MAPPER_NAME);
            mapper.setProtocol(configured.getProtocol());
            mapper.setProtocolMapper(configured.getProtocolMapper());
            mapper.setConfig(configured.getConfig());
        }
        if (isNew) {
            scope.addProtocolMapper(mapper);
        } else {
            scope.updateProtocolMapper(mapper);
        }
    }
}
