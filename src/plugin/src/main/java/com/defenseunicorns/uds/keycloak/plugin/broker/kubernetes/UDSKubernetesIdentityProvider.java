/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.keycloak.broker.kubernetes.KubernetesIdentityProvider;
import org.keycloak.models.KeycloakSession;

/** Keeps the UDS provider class while delegating client assertion verification to Keycloak. */
public class UDSKubernetesIdentityProvider extends KubernetesIdentityProvider {

    public UDSKubernetesIdentityProvider(KeycloakSession session, UDSKubernetesIdentityProviderConfig config) {
        super(session, config);
    }
}
