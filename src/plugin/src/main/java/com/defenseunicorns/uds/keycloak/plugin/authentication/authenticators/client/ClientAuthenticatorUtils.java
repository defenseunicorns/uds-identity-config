/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.ClientAuthUtil;
import org.keycloak.models.ClientModel;
import org.keycloak.util.BasicAuthHelper;

/**
 * Utility class to extract client_id and client_secret from the request.
 * <p>
 * Most of this class has been copied from {@link org.keycloak.authentication.authenticators.client.ClientIdAndSecretAuthenticator} and testing it doesn't make much sense.
 * <p>
 * There's a Keycloak ticket to extract these bits into a utility class: https://github.com/keycloak/keycloak/issues/38095
 */
class ClientAuthenticatorUtils {

    static final ClientAuthenticatorData INVALID = new ClientAuthenticatorData(null, null);

    static ClientAuthenticatorData getExtractClientIdAndSecret(ClientAuthenticationFlowContext context) {
        String client_id = null;
        String clientSecret = null;
        String authorizationHeader = context.getHttpRequest().getHttpHeaders().getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        MediaType mediaType = context.getHttpRequest().getHttpHeaders().getMediaType();
        boolean hasFormData = mediaType != null && mediaType.isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE);

        MultivaluedMap<String, String> formData = hasFormData ? context.getHttpRequest().getDecodedFormParameters() : null;

        if (authorizationHeader != null) {
            String[] usernameSecret = BasicAuthHelper.RFC6749.parseHeader(authorizationHeader);
            if (usernameSecret != null) {
                client_id = usernameSecret[0];
                clientSecret = usernameSecret[1];
            } else {
                if (formData != null && !formData.containsKey(OAuth2Constants.CLIENT_ID)) {
                    Response challengeResponse = Response.status(Response.Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + context.getRealm().getName() + "\"").build();
                    context.challenge(challengeResponse);
                    return INVALID;
                }
            }
        }

        if (formData != null) {
            if (formData.containsKey(OAuth2Constants.CLIENT_ID)) {
                client_id = formData.getFirst(OAuth2Constants.CLIENT_ID);
            }
            if (formData.containsKey(OAuth2Constants.CLIENT_SECRET)) {
                clientSecret = formData.getFirst(OAuth2Constants.CLIENT_SECRET);
            }
        }

        if (client_id == null) {
            client_id = context.getSession().getAttribute("client_id", String.class);
        }

        if (client_id == null) {
            Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.BAD_REQUEST.getStatusCode(), "invalid_client", "Missing client_id parameter");
            context.challenge(challengeResponse);
            return INVALID;
        }

        context.getEvent().client(client_id);

        ClientModel client = context.getSession().clients().getClientByClientId(context.getRealm(), client_id);
        if (client == null) {
            context.failure(AuthenticationFlowError.CLIENT_NOT_FOUND, null);
            return INVALID;
        }

        context.setClient(client);

        if (!client.isEnabled()) {
            context.failure(AuthenticationFlowError.CLIENT_DISABLED, null);
            return INVALID;
        }

        if (client.isPublicClient()) {
            context.success();
            return INVALID;
        }

        if (clientSecret == null) {
            Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.UNAUTHORIZED.getStatusCode(), "unauthorized_client", "Invalid client or Invalid client credentials");
            context.challenge(challengeResponse);
            return INVALID;
        }
        ClientAuthenticatorData result = new ClientAuthenticatorData(client_id, clientSecret);
        return result;
    }

    record ClientAuthenticatorData(String client_id, String clientSecret) {
    }
}
