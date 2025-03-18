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
 * Keycloak Client Policy Executor Factory for the UDS Operator.
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
