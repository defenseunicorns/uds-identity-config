/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.FederatedJWTClientValidator;
import org.keycloak.models.ClientModel;
import org.keycloak.representations.JsonWebToken;

import org.jboss.logging.Logger;

/**
 * Custom JWT client validator that relaxes the {@code client_id} form parameter check.
 *
 * <h2>Why this exists</h2>
 *
 * The upstream {@link FederatedJWTClientValidator} inherits from {@code AbstractJWTClientValidator}
 * which has a {@code private} method {@code validateClient()} that rejects requests where the
 * {@code client_id} form parameter doesn't match the JWT {@code sub} claim. In the Kubernetes
 * service account flow, the JWT subject is the SA identity
 * (e.g. {@code system:serviceaccount:pepr-system:pepr-uds-core}) while the {@code client_id}
 * parameter is the Keycloak client name (e.g. {@code uds-operator}). These intentionally differ.
 *
 * <p>Because {@code validateClient()} is private, we cannot override just that method. We must
 * override {@code validate()} entirely and copy the private helper methods, modifying only the
 * client_id check to log a debug message instead of failing.
 *
 * <p>This is a stop-gap solution until
 * <a href="https://github.com/keycloak/keycloak/pull/48026">https://github.com/keycloak/keycloak/pull/48026</a>
 * is merged upstream, which will make validation methods extensible.
 *
 * <h2>What changed vs upstream</h2>
 *
 * Only one behavioral change in {@code validateClientInternal()}:
 * <pre>
 * // Upstream (fails):
 * if (clientIdParam != null &amp;&amp; !clientIdParam.equals(clientId)) {
 *     return failure("client_id parameter does not match sub claim");
 * }
 *
 * // UDS (logs and continues):
 * if (clientIdParam != null &amp;&amp; !clientIdParam.equals(clientId)) {
 *     LOGGER.debugf("client_id parameter '%s' does not match JWT subject '%s', ignoring", ...);
 * }
 * </pre>
 *
 * All other validation steps (assertion parameters, issuer, signature, audience, expiry,
 * reuse detection) are unchanged from the upstream implementation.
 *
 * @see com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes.UDSKubernetesIdentityProvider
 */
public class UDSFederatedJWTClientValidator extends FederatedJWTClientValidator {

    private static final Logger LOGGER = Logger.getLogger(UDSFederatedJWTClientValidator.class);

    public UDSFederatedJWTClientValidator(ClientAuthenticationFlowContext context,
            SignatureValidator signatureValidator, String expectedTokenIssuer,
            int allowedClockSkew, boolean reusePermitted) throws Exception {
        super(context, signatureValidator, expectedTokenIssuer, allowedClockSkew, reusePermitted);
    }

    @Override
    public boolean validate() {
        return validateClientAssertionParametersInternal() &&
                validateClientInternal() &&
                validateSignatureAlgorithm(getExpectedSignatureAlgorithm()) &&
                validateSignatureInternal() &&
                validateTokenAudience(getExpectedAudiences(), isMultipleAudienceAllowed()) &&
                validateTokenActive(getAllowedClockSkew(), getMaximumExpirationTime(), isReusePermitted());
    }

    /**
     * Copied from {@code AbstractJWTClientValidator.validateClientAssertionParameters()} (private).
     */
    private boolean validateClientAssertionParametersInternal() {
        String clientAssertionType = clientAssertionState.getClientAssertionType();
        String clientAssertion = clientAssertionState.getClientAssertion();

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

    /**
     * Modified from {@code AbstractJWTClientValidator.validateClient()} (private).
     *
     * <p>The only change: when {@code client_id} form parameter doesn't match the JWT subject,
     * this logs a debug message instead of returning a failure. The {@code client_id} parameter
     * is optional in the {@code client_credentials} grant with JWT assertion per RFC 7523.
     */
    private boolean validateClientInternal() {
        JsonWebToken token = clientAssertionState.getToken();

        String clientId = token.getSubject();
        if (clientId == null) {
            return failure("Token sub claim is required");
        }

        // Relaxed check: log instead of fail when client_id doesn't match JWT subject.
        // In the K8s SA flow, client_id is "uds-operator" but sub is the SA identity.
        String clientIdParam = context.getHttpRequest().getDecodedFormParameters().getFirst(OAuth2Constants.CLIENT_ID);
        if (clientIdParam != null && !clientIdParam.equals(clientId)) {
            LOGGER.debugf("client_id parameter '%s' does not match JWT subject '%s', ignoring mismatch",
                clientIdParam, clientId);
        }

        String expectedTokenIssuer = getExpectedTokenIssuer();
        if (expectedTokenIssuer != null && !expectedTokenIssuer.equals(token.getIssuer())) {
            LOGGER.debugf("Expected token issuer '%s' does not match actual issuer '%s'",
                expectedTokenIssuer, token.getIssuer());
            return false;
        }

        ClientModel client = clientAssertionState.getClient();

        if (client == null) {
            context.failure(AuthenticationFlowError.CLIENT_NOT_FOUND, null);
            return false;
        } else {
            context.getEvent().client(client.getClientId());
            context.setClient(client);
        }

        if (!client.isEnabled()) {
            context.failure(AuthenticationFlowError.CLIENT_DISABLED, null);
            return false;
        }

        if (clientAuthenticatorProviderId != null && !clientAuthenticatorProviderId.equals(client.getClientAuthenticatorType())) {
            return false;
        }

        return true;
    }

    /**
     * Copied from {@code AbstractJWTClientValidator.validateSignature()} (private).
     */
    private boolean validateSignatureInternal() {
        return signatureValidator.verifySignature(this);
    }
}
