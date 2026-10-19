/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.broker.kubernetes.KubernetesIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;

/** Preserves the provider ID used by existing UDS realms. */
public class UDSKubernetesIdentityProviderFactory extends KubernetesIdentityProviderFactory {

    public static final String PROVIDER_ID = "uds-kubernetes";

    @Override
    public String getName() {
        return "UDS Kubernetes";
    }

    @Override
    public UDSKubernetesIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new UDSKubernetesIdentityProvider(session, new UDSKubernetesIdentityProviderConfig(model));
    }

    @Override
    public IdentityProviderModel createConfig() {
        return new UDSKubernetesIdentityProviderConfig();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
