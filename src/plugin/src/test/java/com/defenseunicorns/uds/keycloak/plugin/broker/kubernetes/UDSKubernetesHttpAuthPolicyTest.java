/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.junit.jupiter.api.Test;

import com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes.UDSKubernetesHttpAuthPolicy.Mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UDSKubernetesHttpAuthPolicyTest {

    private static final String TOKEN = "pod-sa-token";

    // ---- parseMode fails closed to AUTO ----

    @Test
    void parseModeFailsClosedToAuto() {
        assertEquals(Mode.AUTO, UDSKubernetesHttpAuthPolicy.parseMode(null));
        assertEquals(Mode.AUTO, UDSKubernetesHttpAuthPolicy.parseMode(""));
        assertEquals(Mode.AUTO, UDSKubernetesHttpAuthPolicy.parseMode("bogus"));
        assertEquals(Mode.NEVER, UDSKubernetesHttpAuthPolicy.parseMode("NEVER"));
        assertEquals(Mode.ALWAYS, UDSKubernetesHttpAuthPolicy.parseMode(" ALWAYS "));
    }

    // ---- firstAttemptToken: only ALWAYS authenticates up front ----

    @Test
    void firstAttemptTokenOnlyForAlways() {
        assertEquals(TOKEN, UDSKubernetesHttpAuthPolicy.firstAttemptToken(Mode.ALWAYS, TOKEN));
        assertNull(UDSKubernetesHttpAuthPolicy.firstAttemptToken(Mode.AUTO, TOKEN));
        assertNull(UDSKubernetesHttpAuthPolicy.firstAttemptToken(Mode.NEVER, TOKEN));
    }

    // ---- shouldRetryWithToken: only AUTO retries, only on 401/403, only with a token ----

    @Test
    void autoRetriesOnAuthChallenge() {
        assertTrue(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.AUTO, 401, TOKEN));
        assertTrue(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.AUTO, 403, TOKEN));
    }

    @Test
    void autoDoesNotRetryOnSuccessOrOtherStatuses() {
        assertFalse(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.AUTO, 200, TOKEN));
        assertFalse(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.AUTO, 404, TOKEN));
        assertFalse(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.AUTO, 500, TOKEN));
    }

    @Test
    void noRetryWithoutToken() {
        assertFalse(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.AUTO, 401, null));
    }

    @Test
    void neverAndAlwaysDoNotRetry() {
        // NEVER stays anonymous; ALWAYS already sent the token on the first attempt.
        assertFalse(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.NEVER, 401, TOKEN));
        assertFalse(UDSKubernetesHttpAuthPolicy.shouldRetryWithToken(Mode.ALWAYS, 401, TOKEN));
    }
}
