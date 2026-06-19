/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Controls whether Keycloak's pod service-account token is attached to outbound discovery/JWKS requests.
 * <p>
 * Stock Keycloak forwards the pod token whenever the token issuer equals the configured IdP issuer, which leaks
 * the token to external/public endpoints (EKS/AKS OIDC, RKE2 S3) that neither need nor accept it (S3 even rejects
 * an unknown {@code Authorization} with HTTP 400). Rather than guess from the URL whether a destination is
 * "in-cluster", {@link Mode#AUTO} asks the endpoint: send the request anonymously first, and only retry with the
 * token if the endpoint answers with a 401/403 challenge. Public issuers (S3/EKS/AKS) answer 200 anonymously so
 * they never receive the token; the in-cluster API (which serves its JWKS at the apiserver address and requires
 * auth) challenges, so it does.
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> this whole policy exists to compensate for stock Keycloak's token
 * forwarding. Remove with the rest of the plugin once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> is resolved.
 */
final class UDSKubernetesHttpAuthPolicy {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    enum Mode {
        /** Try anonymously first; attach the pod token only if the endpoint returns a 401/403 challenge. */
        AUTO,
        /** Never attach the pod token (force anonymous; for fully public issuers). */
        NEVER,
        /** Always attach the pod token on the first request (compatibility/debugging). */
        ALWAYS
    }

    private UDSKubernetesHttpAuthPolicy() {
    }

    /**
     * Parse a Mode, failing closed to {@link Mode#AUTO} on null/blank/unknown values.
     */
    static Mode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return Mode.AUTO;
        }
        try {
            return Mode.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown jwksAuthMode '{}', defaulting to AUTO", value);
            return Mode.AUTO;
        }
    }

    /**
     * The token to attach to the FIRST request: only {@link Mode#ALWAYS} authenticates up front. AUTO starts
     * anonymous (and may retry); NEVER stays anonymous.
     */
    static String firstAttemptToken(Mode mode, String token) {
        return mode == Mode.ALWAYS ? token : null;
    }

    /**
     * Whether to retry a request WITH the token after an anonymous attempt returned {@code status}. Only AUTO
     * retries, and only on a 401/403 auth challenge when a token is available.
     */
    static boolean shouldRetryWithToken(Mode mode, int status, String token) {
        return mode == Mode.AUTO && token != null && (status == 401 || status == 403);
    }
}
