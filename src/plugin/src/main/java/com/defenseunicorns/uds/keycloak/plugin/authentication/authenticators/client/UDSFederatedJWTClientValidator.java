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

/**
 * Federated JWT client validator with the fix from keycloak/keycloak#48026 backported.
 * <p>
 * In Keycloak 26.6.x {@code AbstractJWTClientValidator.validateClient()} compares the {@code client_id} request
 * parameter against the JWT {@code sub} claim. For Kubernetes service accounts the {@code sub} is
 * {@code system:serviceaccount:<ns>:<name>}, so any caller that sends a {@code client_id} (e.g. {@code uds-fleet-admin})
 * is rejected with "client_id parameter does not match sub claim". This validator instead compares {@code client_id}
 * to the resolved {@link ClientModel#getClientId()} after lookup (sub still required; missing client_id passes).
 * <p>
 * {@code validateClient()} is private upstream, so {@link #validate()} is overridden wholesale. The override and
 * its helpers are copied/adapted from Keycloak 26.6.3 AbstractJWTClientValidator (validate / validateClient /
 * validateClientAssertionParameters), keeping all other validations equivalent to upstream:
 * https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/authenticators/client/AbstractJWTClientValidator.java#L66-L162
 * <p>
 * Remove this class when the UDS Keycloak runtime includes keycloak/keycloak#48026. This is part of the broader
 * Kubernetes-federated-auth workaround tracked by
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a>; when that is resolved
 * the whole custom plugin (and this class) goes away in favor of the stock {@code kubernetes} provider.
 */
public class UDSFederatedJWTClientValidator extends FederatedJWTClientValidator {

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

        String expectedTokenIssuer = getExpectedTokenIssuer();
        if (expectedTokenIssuer != null && !expectedTokenIssuer.equals(token.getIssuer())) {
            return false;
        }

        context.getEvent().client(client.getClientId());
        context.setClient(client);

        if (!client.isEnabled()) {
            context.failure(AuthenticationFlowError.CLIENT_DISABLED, null);
            return false;
        }
        return true;
    }
}
