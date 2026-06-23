/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.broker.kubernetes.KubernetesIdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.RealmModel;
import org.keycloak.utils.KeycloakSessionUtil;

/**
 * Config for the UDS Kubernetes identity provider. Adds explicit, removable controls on top of the stock
 * Kubernetes provider so issuer discovery is understandable and tunable per environment.
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> these extra fields exist only for the bridge plugin. When
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> is resolved, drop this
 * class and use the stock {@link KubernetesIdentityProviderConfig}.
 */
public class UDSKubernetesIdentityProviderConfig extends KubernetesIdentityProviderConfig {

    public static final String ISSUER_DISCOVERY_URL = "issuerDiscoveryUrl";
    public static final String AUTOMATIC_ISSUER_DISCOVERY = "automaticIssuerDiscovery";
    /** Config key the inherited {@link #getIssuer()} reads. */
    public static final String ISSUER = "issuer";

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

    /**
     * Validation hook (fires on IdP create/update, including realm import). Resolves and persists the issuer
     * before the standard issuer-uniqueness validation.
     */
    @Override
    public void validate(RealmModel realm) {
        resolveAndPersistIssuer();
        super.validate(realm);
    }

    /**
     * Resolve-and-persist step, isolated for unit testing. When no issuer is configured and automatic discovery is
     * on, resolve it from the cluster and store it; an explicitly configured issuer wins and skips discovery; a
     * discovery failure throws (so an unverified issuer is never persisted).
     */
    void resolveAndPersistIssuer() {
        String configured = getConfig().get(ISSUER);
        if ((configured == null || configured.isBlank()) && isAutomaticIssuerDiscovery()) {
            String resolved = KubernetesUtils.resolveIssuer(KeycloakSessionUtil.getKeycloakSession(), getIssuerDiscoveryUrl());
            if (resolved == null) {
                throw new IllegalArgumentException("Could not resolve Kubernetes issuer from " + getIssuerDiscoveryUrl()
                        + "; set '" + ISSUER + "' explicitly or disable '" + AUTOMATIC_ISSUER_DISCOVERY + "'");
            }
            getConfig().put(ISSUER, resolved);
        }
    }
}
