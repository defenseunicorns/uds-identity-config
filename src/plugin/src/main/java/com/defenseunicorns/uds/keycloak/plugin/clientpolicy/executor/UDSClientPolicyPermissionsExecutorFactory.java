/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProviderFactory;

import java.util.Collections;
import java.util.List;

/**
 * Factory for creating and registering the UDS Client Policy Executor with Keycloak.
 *
 * This class is needed to integrate the custom UDS security hardening logic into Keycloak's
 * client management process. It creates instances of UDSClientPolicyPermissionsExecutor, which
 * enforce policies such as:
 * - Marking clients as created by the UDS Operator by setting the "created-by=uds-operator" attribute.
 * - Restricting operations on clients not owned by the UDS Operator or not following the naming convention.
 * - Disabling the "Full Scope Allowed" feature and limiting client scopes to an allowed set.
 *
 * Without this factory, Keycloak would not be able to instantiate and manage the custom policy
 * executor, and the UDS-specific client policies would not be enforced.
 */
public class UDSClientPolicyPermissionsExecutorFactory implements ClientPolicyExecutorProviderFactory {

    public static final String PROVIDER_ID = "uds-operator-permissions";

    @Override
    public UDSClientPolicyPermissionsExecutor create(KeycloakSession session) {
        return new UDSClientPolicyPermissionsExecutor();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
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
        return Collections.emptyList();
    }
}
