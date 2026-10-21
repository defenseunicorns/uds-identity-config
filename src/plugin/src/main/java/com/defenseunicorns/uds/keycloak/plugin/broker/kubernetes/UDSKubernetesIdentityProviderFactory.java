/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.Config;
import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory;
import org.keycloak.common.Profile;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

import java.util.Map;

/**
 * Registers the UDS Kubernetes identity provider. Client resolution uses the stock client-assertion strategy
 * (the default), which works because the provider persists the resolved issuer onto the IdP at validation time.
 * <p>
 * Modeled on the upstream Kubernetes / SPIFFE identity provider factories.
 * <p>
 * <b>WORKAROUND:</b> temporary bridge. Once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> ships destination-based
 * token forwarding and managed-issuer discovery in the runtime version UDS ships, delete this whole plugin and set
 * the realm IdP back to {@code providerId: "kubernetes"}.
 */
public class UDSKubernetesIdentityProviderFactory extends AbstractIdentityProviderFactory<UDSKubernetesIdentityProvider>
        implements EnvironmentDependentProviderFactory, ClientAssertionIdentityProviderFactory {

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
    public Map<String, String> parseConfig(KeycloakSession session, String configString) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IdentityProviderModel createConfig() {
        return new UDSKubernetesIdentityProviderConfig();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return Profile.isFeatureEnabled(Profile.Feature.KUBERNETES_SERVICE_ACCOUNTS);
    }
}
