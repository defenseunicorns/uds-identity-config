/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.jboss.logging.Logger;

import static org.keycloak.broker.kubernetes.KubernetesConstants.SERVICE_ACCOUNT_TOKEN_PATH;

/**
 * Shared utilities for Kubernetes identity provider classes.
 */
final class KubernetesUtils {

    private static final Logger LOGGER = Logger.getLogger(KubernetesUtils.class);

    private KubernetesUtils() {
    }

    /**
     * Reads the Kubernetes service account token from the standard mounted path.
     *
     * <p>On managed Kubernetes clusters (EKS, AKS, GKE), the SA token must be included in
     * requests to the K8s API server's OIDC discovery and JWKS endpoints. The upstream Keycloak
     * code conditionally includes this token based on an issuer match, which fails on managed
     * clusters. Our custom classes use this method to always include the token.
     *
     * @return the SA token string, or {@code null} if the file doesn't exist or can't be read
     */
    static String readServiceAccountToken() {
        try {
            var path = Paths.get(SERVICE_ACCOUNT_TOKEN_PATH);
            if (!Files.exists(path)) {
                return null;
            }
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            LOGGER.warn("Failed to read service account token", e);
            return null;
        }
    }
}
