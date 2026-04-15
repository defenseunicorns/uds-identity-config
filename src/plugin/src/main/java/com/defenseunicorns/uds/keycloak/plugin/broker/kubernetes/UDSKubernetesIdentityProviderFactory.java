/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.Config;
import org.keycloak.broker.kubernetes.KubernetesIdentityProviderConfig;
import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory;
import org.keycloak.common.Profile;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

import com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client.UDSClientAssertionStrategy;

/**
 * Factory for {@link UDSKubernetesIdentityProvider}.
 *
 * <p>Registered as a {@link ClientAssertionIdentityProviderFactory} SPI, which causes
 * Keycloak's {@code FederatedJWTClientAuthenticator.postInit()} to collect our
 * {@link UDSClientAssertionStrategy} into the strategies list. Because custom strategies
 * are added before the {@code DefaultClientAssertionStrategy}, our strategy is tried first
 * for JWT bearer assertions — acting as a superset of the default behavior.
 *
 * @see UDSClientAssertionStrategy
 * @see UDSKubernetesIdentityProvider
 */
public class UDSKubernetesIdentityProviderFactory
        extends AbstractIdentityProviderFactory<UDSKubernetesIdentityProvider>
        implements ClientAssertionIdentityProviderFactory, EnvironmentDependentProviderFactory {

    public static final String PROVIDER_ID = "uds-kubernetes";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getName() {
        return "UDS Kubernetes";
    }

    @Override
    public UDSKubernetesIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new UDSKubernetesIdentityProvider(session, new KubernetesIdentityProviderConfig(model));
    }

    @Override
    public IdentityProviderModel createConfig() {
        return new KubernetesIdentityProviderConfig();
    }

    @Override
    public ClientAssertionStrategy getClientAssertionStrategy() {
        return new UDSClientAssertionStrategy();
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return Profile.isFeatureEnabled(Profile.Feature.KUBERNETES_SERVICE_ACCOUNTS);
    }
}
