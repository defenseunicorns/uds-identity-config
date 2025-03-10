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

public class UDSClientPolicyPermissionsExecutorFactory implements ClientPolicyExecutorProviderFactory {

    public static final String PROVIDER_ID = "uds-operator-permissions";

    public static final String AUTO_CONFIGURE = "auto-configure";

    private static final ProviderConfigProperty AUTO_CONFIGURE_PROPERTY = new ProviderConfigProperty(
            AUTO_CONFIGURE, "Auto-configure", "If On, the configuration of the client will be auto-configured to disable fullScopeAllowed during client creation or update." +
            "If off, the clients are validated to not have fullScopeAllowed enabled during create/update client", ProviderConfigProperty.BOOLEAN_TYPE, true);

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
        return "When present, then registered/updated clients will contain additional attributes and the UDS Operator identity will be verified for trimmed down permissions";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.singletonList(AUTO_CONFIGURE_PROPERTY);
    }
}
