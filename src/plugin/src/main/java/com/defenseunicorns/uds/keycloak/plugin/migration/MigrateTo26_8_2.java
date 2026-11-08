/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.defenseunicorns.uds.keycloak.plugin.UDSAdminApiAudienceMapper;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutor;
import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutorConfiguration;
import org.keycloak.json.RawJsonValue;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.util.JsonSerialization;
import org.keycloak.representations.idm.ClientPolicyExecutorRepresentation;
import org.keycloak.representations.idm.ClientProfileRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutorFactory.PROVIDER_ID;

public final class MigrateTo26_8_2 implements RealmMigration {
    static final String SCOPE_NAME = "kc-admin-api";
    static final String ALLOWED_MAPPERS_ATTRIBUTE = "allowedProtocolMapperTypesForClientAdministrators";
    private static final String MAPPER_NAME = "uds-admin-api-audience";
    private static final List<String> ADMIN_CLIENTS = List.of(
            "uds-operator", "uds-fleet-admin", "uds-opentofu-client", "admin-cli", "security-admin-console");

    @Override
    public String version() {
        return "26.8.2";
    }

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
        allowProtocolMappers(session, realm);
    }

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

    private void allowProtocolMappers(KeycloakSession session, RealmModel realm) {
        Set<String> allowed = new LinkedHashSet<>(configuredAllowedMappers(session, realm));
        String existing = realm.getAttribute(ALLOWED_MAPPERS_ATTRIBUTE);
        if (existing != null) {
            Stream.of(existing.split("," )).map(String::trim).filter(value -> !value.isEmpty()).forEach(allowed::add);
        }
        realm.setAttribute(ALLOWED_MAPPERS_ATTRIBUTE, String.join(",", allowed));
    }


    private Set<String> configuredAllowedMappers(KeycloakSession session, RealmModel realm) {
        ClientProfilesRepresentation profiles;
        try {
            profiles = session.clientPolicy().getClientProfiles(realm, false);
        } catch (ClientPolicyException e) {
            throw new IllegalStateException("Could not read persisted client profiles", e);
        }
        if (profiles == null || profiles.getProfiles() == null) {
            return Set.of();
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (ClientProfileRepresentation profile : profiles.getProfiles()) {
            if (profile.getExecutors() == null) continue;
            for (ClientPolicyExecutorRepresentation executor : profile.getExecutors()) {
                if (!PROVIDER_ID.equals(executor.getExecutorProviderId())) continue;
                UDSClientPolicyPermissionsExecutorConfiguration configuration = new UDSClientPolicyPermissionsExecutorConfiguration();
                if (executor.getConfiguration() != null) {
                    JsonNode config = RawJsonValue.unwrap(JsonNode.class, executor.getConfiguration());
                    if (config != null && !config.isNull()) {
                        configuration = JsonSerialization.mapper.convertValue(config, UDSClientPolicyPermissionsExecutorConfiguration.class);
                    }
                }
                allowed.addAll(UDSClientPolicyPermissionsExecutor.resolveAllowedProtocolMappers(configuration, session));
            }
        }
        return allowed;
    }
}
