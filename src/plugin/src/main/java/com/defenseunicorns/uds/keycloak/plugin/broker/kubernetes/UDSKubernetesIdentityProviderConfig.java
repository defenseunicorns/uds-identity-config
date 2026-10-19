/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.broker.kubernetes.KubernetesIdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

import static org.keycloak.broker.kubernetes.KubernetesConstants.DEFAULT_KUBERNETES_API_SERVER_URL;

/** Keeps the existing UDS discovery controls while using Keycloak's discovery implementation. */
public class UDSKubernetesIdentityProviderConfig extends KubernetesIdentityProviderConfig {

    public static final String AUTOMATIC_ISSUER_DISCOVERY = "automaticIssuerDiscovery";

    public UDSKubernetesIdentityProviderConfig() {
    }

    public UDSKubernetesIdentityProviderConfig(IdentityProviderModel model) {
        super(model);
    }

    public boolean isAutomaticIssuerDiscovery() {
        return !Boolean.FALSE.toString().equals(getConfig().get(AUTOMATIC_ISSUER_DISCOVERY));
    }

    @Override
    public String getIssuerDiscoveryUrl() {
        String issuer = getConfig().get(ISSUER);
        // UDS gives an explicit issuer precedence over discovery, unlike the upstream default.
        if (!isAutomaticIssuerDiscovery() || (issuer != null && !issuer.isBlank())) {
            return null;
        }
        String discoveryUrl = super.getIssuerDiscoveryUrl();
        return discoveryUrl != null && !discoveryUrl.isBlank() ? discoveryUrl : DEFAULT_KUBERNETES_API_SERVER_URL;
    }
}
