/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client.UDSClientAssertionStrategy;
import org.keycloak.Config;
import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory;
import org.keycloak.common.Profile;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

import java.util.Map;

/**
 * Registers the UDS Kubernetes identity provider and contributes {@link UDSClientAssertionStrategy} so the
 * federated-JWT client authenticator can resolve clients backed by this provider (including managed/external
 * issuers where the stock issuer-match lookup fails).
 * <p>
 * Modeled on Keycloak 26.6.3 KubernetesIdentityProviderFactory / SpiffeIdentityProviderFactory:
 * https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/broker/kubernetes/KubernetesIdentityProviderFactory.java
 * <p>
 * <b>WORKAROUND (keycloak#49039):</b> temporary bridge. Once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> ships destination-based
 * token forwarding, managed-issuer discovery, and the keycloak#48026 client_id validation in the runtime version
 * UDS ships, delete this whole plugin and set the realm IdP back to {@code providerId: "kubernetes"}.
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

    @Override
    public ClientAssertionStrategy getClientAssertionStrategy() {
        return new UDSClientAssertionStrategy();
    }
}
