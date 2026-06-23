/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.FederatedJWTClientValidator;
import org.keycloak.models.ClientModel;
import org.keycloak.representations.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Federated JWT client validator with the fix from
 * <a href="https://github.com/keycloak/keycloak/issues/48026">keycloak/keycloak#48026</a> backported.
 * <p>
 * Upstream {@code AbstractJWTClientValidator.validateClient()} compares the {@code client_id} request parameter
 * against the JWT {@code sub} claim. For Kubernetes service accounts the {@code sub} is
 * {@code system:serviceaccount:<ns>:<name>}, so any caller that sends a {@code client_id} (e.g. {@code uds-fleet-admin})
 * is rejected with "client_id parameter does not match sub claim". This validator instead compares {@code client_id}
 * to the resolved {@link ClientModel#getClientId()} after lookup (sub still required; missing client_id passes).
 * <p>
 * {@code validateClient()} is private upstream, so {@link #validate()} is overridden wholesale, copying upstream's
 * validate / validateClient / validateClientAssertionParameters logic and keeping all other validations equivalent.
 * <p>
 * Remove this class once the UDS Keycloak runtime includes keycloak/keycloak#48026 — part of the broader
 * Kubernetes-federated-auth workaround tracked by
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a>, after which the whole
 * plugin reverts to the stock {@code kubernetes} provider.
 */
public class UDSFederatedJWTClientValidator extends FederatedJWTClientValidator {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public UDSFederatedJWTClientValidator(ClientAuthenticationFlowContext context, SignatureValidator signatureValidator,
                                          String expectedTokenIssuer, int allowedClockSkew, boolean reusePermitted,
                                          String... validAudiences) throws Exception {
        super(context, signatureValidator, expectedTokenIssuer, allowedClockSkew, reusePermitted, validAudiences);
    }

    /**
     * The #48026 decision, isolated for unit testing: a {@code client_id} request parameter is acceptable when it
     * is absent, or when it equals the resolved client's id. (Upstream incorrectly compares it to the JWT sub.)
     */
    static boolean clientIdParamMatches(String clientIdParam, String resolvedClientId) {
        return clientIdParam == null || clientIdParam.equals(resolvedClientId);
    }

    @Override
    public boolean validate() {
        return validateAssertionParameters()
                && validateClientWithResolvedClientId()
                && validateSignatureAlgorithm(getExpectedSignatureAlgorithm())
                && signatureValidator.verifySignature(this)
                && validateTokenAudience(getExpectedAudiences(), isMultipleAudienceAllowed())
                && validateTokenActive(getAllowedClockSkew(), getMaximumExpirationTime(), isReusePermitted());
    }

    private boolean validateAssertionParameters() {
        String clientAssertionType = getState().getClientAssertionType();
        String clientAssertion = getState().getClientAssertion();

        if (clientAssertionType == null) {
            return failure("Parameter client_assertion_type is missing");
        }
        if (!expectedClientAssertionType.equals(clientAssertionType)) {
            return failure("Parameter client_assertion_type has value '"
                    + clientAssertionType + "' but expected is '" + expectedClientAssertionType + "'");
        }
        if (clientAssertion == null) {
            return failure("client_assertion parameter missing");
        }
        return true;
    }

    private boolean validateClientWithResolvedClientId() {
        JsonWebToken token = getState().getToken();

        if (token.getSubject() == null) {
            return failure("Token sub claim is required");
        }

        // Deviation: the client-null check is hoisted above the client_id comparison so the #48026 fix can compare
        // against the resolved client id. When no client resolves this returns CLIENT_NOT_FOUND (upstream would
        // first reject on the client_id/sub mismatch); no validation is dropped.
        ClientModel client = getState().getClient();
        if (client == null) {
            context.failure(AuthenticationFlowError.CLIENT_NOT_FOUND, null);
            return false;
        }

        // #48026 fix: compare client_id against the resolved client, not the JWT sub.
        String clientIdParam = context.getHttpRequest().getDecodedFormParameters().getFirst(OAuth2Constants.CLIENT_ID);
        if (!clientIdParamMatches(clientIdParam, client.getClientId())) {
            return failure("client_id parameter does not match the authenticated client");
        }

        // Issuer mismatch returns false without a failure()/event, matching upstream. The log is a UDS addition:
        // the expected issuer can come from runtime discovery, so a discovery/token drift would otherwise reject
        // silently.
        String expectedTokenIssuer = getExpectedTokenIssuer();
        if (expectedTokenIssuer != null && !expectedTokenIssuer.equals(token.getIssuer())) {
            logger.warn("Token issuer '{}' does not match expected issuer '{}'", token.getIssuer(), expectedTokenIssuer);
            return false;
        }

        context.getEvent().client(client.getClientId());
        context.setClient(client);

        if (!client.isEnabled()) {
            context.failure(AuthenticationFlowError.CLIENT_DISABLED, null);
            return false;
        }
        // Upstream's trailing clientAuthenticatorProviderId check is omitted: the federated flow constructs the
        // parent with a null clientAuthenticatorProviderId, so that branch is dead code here.
        return true;
    }
}
