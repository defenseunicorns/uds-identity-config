/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.broker.kubernetes.KubernetesIdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

/**
 * Config for the UDS Kubernetes identity provider. Adds explicit, removable controls on top of the stock
 * Kubernetes provider so issuer discovery and pod-token forwarding are understandable and tunable per environment.
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> these extra fields exist only for the bridge plugin. When
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> is resolved, drop this
 * class and use the stock {@link KubernetesIdentityProviderConfig}.
 */
public class UDSKubernetesIdentityProviderConfig extends KubernetesIdentityProviderConfig {

    public static final String ISSUER_DISCOVERY_URL = "issuerDiscoveryUrl";
    public static final String AUTOMATIC_ISSUER_DISCOVERY = "automaticIssuerDiscovery";
    public static final String JWKS_AUTH_MODE = "jwksAuthMode";

    public static final String DEFAULT_IN_CLUSTER_URL = "https://kubernetes.default.svc.cluster.local";

    public UDSKubernetesIdentityProviderConfig() {
    }

    public UDSKubernetesIdentityProviderConfig(IdentityProviderModel model) {
        super(model);
    }

    /**
     * Base URL used to fetch OIDC discovery + JWKS. Falls back to the configured issuer, then the in-cluster API.
     */
    public String getIssuerDiscoveryUrl() {
        String discoveryUrl = getConfig().get(ISSUER_DISCOVERY_URL);
        if (discoveryUrl != null && !discoveryUrl.isBlank()) {
            return discoveryUrl;
        }
        String issuer = getIssuer();
        return (issuer != null && !issuer.isBlank()) ? issuer : DEFAULT_IN_CLUSTER_URL;
    }

    public boolean isAutomaticIssuerDiscovery() {
        return Boolean.parseBoolean(getConfig().get(AUTOMATIC_ISSUER_DISCOVERY));
    }

    /** Token-forwarding policy; fails closed to AUTO_IN_CLUSTER_ONLY on missing/invalid values. */
    public UDSKubernetesHttpAuthPolicy.Mode getJwksAuthMode() {
        return UDSKubernetesHttpAuthPolicy.parseMode(getConfig().get(JWKS_AUTH_MODE));
    }
}
