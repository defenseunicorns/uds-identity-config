/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-tests the #48026 client_id decision in isolation. The full validate() chain (signature, audience,
 * expiration) is exercised by the uds-core cross-distro integration test, since constructing the validator
 * requires a complete client-authentication flow context.
 */
class UDSFederatedJWTClientValidatorTest {

    @Test
    void missingClientIdParamPasses() {
        // Fleet authenticates without a client_id; the client is resolved from the assertion.
        assertTrue(UDSFederatedJWTClientValidator.clientIdParamMatches(null, "uds-fleet-admin"));
    }

    @Test
    void matchingClientIdParamPasses() {
        assertTrue(UDSFederatedJWTClientValidator.clientIdParamMatches("uds-fleet-admin", "uds-fleet-admin"));
    }

    @Test
    void wrongClientIdParamFails() {
        assertFalse(UDSFederatedJWTClientValidator.clientIdParamMatches("some-other-client", "uds-fleet-admin"));
    }

    @Test
    void clientIdMatchesResolvedClientNotJwtSub() {
        // Upstream bug compared against the JWT sub (system:serviceaccount:...). The fix compares against the
        // resolved client id, so a client_id equal to the service-account sub must NOT be accepted.
        String saSub = "system:serviceaccount:uds-fleet-command:uds-fleet-command-sa";
        assertFalse(UDSFederatedJWTClientValidator.clientIdParamMatches(saSub, "uds-fleet-admin"));
    }
}
