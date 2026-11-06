/*
 * Copyright 2024-2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProviderFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Factory for creating and registering the UDS Client Policy Executor with Keycloak.
 * <p>
 * This class is needed to integrate the custom UDS security hardening logic into Keycloak's
 * client management process. It creates instances of UDSClientPolicyPermissionsExecutor, which
 * enforce policies such as:
 * - Marking clients as created by the UDS Operator by setting the "created-by=uds-operator" attribute.
 * - Restricting operations on clients not owned by the UDS Operator or not following the naming convention.
 * - Limiting client scopes to an allowed set.
 * <p>
 * Without this factory, Keycloak would not be able to instantiate and manage the custom policy
 * executor, and the UDS-specific client policies would not be enforced.
 */
public class UDSClientPolicyPermissionsExecutorFactory implements ClientPolicyExecutorProviderFactory, ProviderEventListener {

    public static final String PROVIDER_ID = "uds-operator-permissions";
    private static final Logger LOG = Logger.getLogger(UDSClientPolicyPermissionsExecutorFactory.class);
    private static final String ADMIN_SCOPE = "kc-admin-api";
    private static final String ALLOWED_MAPPER_TYPES = "allowedProtocolMapperTypesForClientAdministrators";
    private static final String ADDITIONAL_MAPPER_TYPES_ENV = "REALM_SECURITY_HARDENING_ADDITIONAL_PROTOCOL_MAPPERS";
    private static final String MIGRATION_COMPLETE = "uds.keycloak.26.8.2.migrationComplete";
    private static final List<String> ADMIN_CLIENTS = List.of("uds-operator", "uds-fleet-admin", "uds-opentofu-client");

    public static final String ADDITIONAL_ALLOWED_PROTOCOL_MAPPER_TYPES = "allowed-protocol-mappers";
    public static final String ADDITIONAL_ALLOWED_PROTOCOL_MAPPER_TYPES_AS_STRING = "allowed-protocol-mappers-as-string";
    public static final String ADDITIONAL_ALLOWED_CLIENT_SCOPES = "allowed-client-scopes";
    public static final String ADDITIONAL_ALLOWED_CLIENT_SCOPES_AS_STRING = "allowed-client-scopes-as-string";
    public static final String USE_DEFAULT_ALLOWED_PROTOCOL_MAPPER_TYPES = "use-default-allowed-protocol-mappers";
    public static final String USE_DEFAULT_ALLOWED_CLIENT_SCOPES = "use-default-allowed-client-scopes";

    protected static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    ProviderConfigProperty additionalAllowedProtocolMappers = new ProviderConfigProperty();
    ProviderConfigProperty useDefaultAllowedProtocolMappers = new ProviderConfigProperty();
    ProviderConfigProperty additionalAllowedClientScopes = new ProviderConfigProperty();
    ProviderConfigProperty useDefaultAllowedClientScopes = new ProviderConfigProperty();

    {
        additionalAllowedProtocolMappers.setName(ADDITIONAL_ALLOWED_PROTOCOL_MAPPER_TYPES);
        additionalAllowedProtocolMappers.setLabel("Additional Allowed Protocol Mappers");
        additionalAllowedProtocolMappers.setHelpText("Additional Allowed Protocol Mappers");
        additionalAllowedProtocolMappers.setType(ProviderConfigProperty.MULTIVALUED_LIST_TYPE);
        configProperties.add(additionalAllowedProtocolMappers);

        useDefaultAllowedProtocolMappers.setName(USE_DEFAULT_ALLOWED_PROTOCOL_MAPPER_TYPES);
        useDefaultAllowedProtocolMappers.setLabel("Use UDS Default Allowed Protocol Mappers");
        useDefaultAllowedProtocolMappers.setHelpText("Setting this to true results in default OIDC and UDS Protocol Mappers to be whitelisted");
        useDefaultAllowedProtocolMappers.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        useDefaultAllowedProtocolMappers.setDefaultValue(Boolean.TRUE.toString());
        configProperties.add(useDefaultAllowedProtocolMappers);

        additionalAllowedClientScopes.setName(ADDITIONAL_ALLOWED_CLIENT_SCOPES);
        additionalAllowedClientScopes.setLabel("Additional Allowed Client Scopes");
        additionalAllowedClientScopes.setHelpText("Additional Allowed Client Scopes");
        additionalAllowedClientScopes.setType(ProviderConfigProperty.MULTIVALUED_STRING_TYPE);
        configProperties.add(additionalAllowedClientScopes);

        useDefaultAllowedClientScopes.setName(USE_DEFAULT_ALLOWED_CLIENT_SCOPES);
        useDefaultAllowedClientScopes.setLabel("Use UDS Default Client Scopes");
        useDefaultAllowedClientScopes.setHelpText("Setting this to true results in default OIDC and UDS Client Scopes to be whitelisted");
        useDefaultAllowedClientScopes.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        useDefaultAllowedClientScopes.setDefaultValue(Boolean.TRUE.toString());
        configProperties.add(useDefaultAllowedClientScopes);
    }

    @Override
    public UDSClientPolicyPermissionsExecutor create(KeycloakSession session) {
        return new UDSClientPolicyPermissionsExecutor(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public synchronized void postInit(KeycloakSessionFactory factory) {
        // Factory init is tricky and might be called multiple times.
        // We initialize everything at the instance construction time and keep updating the options.
        additionalAllowedProtocolMappers.setOptions(getProtocolMapperFactoryIds(factory));
        factory.register(this);
    }

    @Override
    public void onEvent(ProviderEvent event) {
        if (event instanceof PostMigrationEvent postMigration) {
            KeycloakModelUtils.runJobInTransaction(postMigration.getFactory(), this::migrateAdminApiConfiguration);
        }
    }

    void migrateAdminApiConfiguration(KeycloakSession session) {
        RealmModel realm = session.realms().getRealmByName("uds");
        if (realm == null || "true".equals(realm.getAttribute(MIGRATION_COMPLETE))) {
            return;
        }

        // Keycloak creates the scope on upgrade and after a fresh realm import.
        ClientScopeModel scope = realm.getClientScopesStream()
                .filter(candidate -> ADMIN_SCOPE.equals(candidate.getName()))
                .findFirst()
                .orElse(null);
        if (scope == null) {
            LOG.warnf("Client scope %s is unavailable in the uds realm; UDS Admin API clients cannot be migrated yet", ADMIN_SCOPE);
            return;
        }

        for (String clientId : ADMIN_CLIENTS) {
            ClientModel client = realm.getClientByClientId(clientId);
            if (client != null && !client.getClientScopes(true).containsKey(ADMIN_SCOPE)) {
                client.addClientScope(scope, true);
                LOG.infof("Assigned %s client scope to %s", ADMIN_SCOPE, clientId);
            }
        }

        Set<String> allowedTypes = new LinkedHashSet<>();
        addMapperTypes(allowedTypes, realm.getAttribute(ALLOWED_MAPPER_TYPES));
        allowedTypes.addAll(UDSClientPolicyPermissionsExecutor.DEFAULT_ALLOWED_PROTOCOL_MAPPERS);
        addMapperTypes(allowedTypes, System.getenv(ADDITIONAL_MAPPER_TYPES_ENV));
        String updated = String.join(",", allowedTypes);
        if (!updated.equals(realm.getAttribute(ALLOWED_MAPPER_TYPES))) {
            realm.setAttribute(ALLOWED_MAPPER_TYPES, updated);
            LOG.info("Updated delegated client administrator protocol mapper types in the uds realm");
        }
        realm.setAttribute(MIGRATION_COMPLETE, "true");
    }

    private static void addMapperTypes(Set<String> target, String configured) {
        if (configured != null) {
            Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(target::add);
        }
    }

    private List<String> getProtocolMapperFactoryIds(KeycloakSessionFactory sessionFactory) {
        return sessionFactory.getProviderFactoriesStream(ProtocolMapper.class)
                .map(ProviderFactory::getId)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "UDS Security Hardening for managing Clients";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of(additionalAllowedProtocolMappers,
                useDefaultAllowedProtocolMappers,
                additionalAllowedClientScopes,
                useDefaultAllowedClientScopes);
    }
}
