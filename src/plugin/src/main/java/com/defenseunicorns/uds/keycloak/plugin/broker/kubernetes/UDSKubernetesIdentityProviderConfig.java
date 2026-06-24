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
 * <p><b>WORKAROUND:</b> these extra fields exist only for the bridge plugin. When
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
     * Base URL queried once (at validation) to resolve the issuer. Defaults to the in-cluster Kubernetes API.
     * This is NOT used for key loading — keys are fetched from the resolved {@link #getIssuer() issuer} itself.
     */
    public String getIssuerDiscoveryUrl() {
        String discoveryUrl = getConfig().get(ISSUER_DISCOVERY_URL);
        return (discoveryUrl != null && !discoveryUrl.isBlank()) ? discoveryUrl : DEFAULT_IN_CLUSTER_URL;
    }

    /**
     * Default ON: only an explicit {@code "false"} disables it (matches upstream
     * <a href="https://github.com/keycloak/keycloak/pull/50224">keycloak/keycloak#50224</a>).
     */
    public boolean isAutomaticIssuerDiscovery() {
        return !Boolean.FALSE.toString().equals(getConfig().get(AUTOMATIC_ISSUER_DISCOVERY));
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
            // resolveIssuer throws IllegalArgumentException (cause chained) on failure, so an unverified issuer is
            // never persisted and the real cause surfaces at IdP validation.
            String resolved = KubernetesUtils.resolveIssuer(KeycloakSessionUtil.getKeycloakSession(), getIssuerDiscoveryUrl());
            getConfig().put(ISSUER, resolved);
        }
    }
}
