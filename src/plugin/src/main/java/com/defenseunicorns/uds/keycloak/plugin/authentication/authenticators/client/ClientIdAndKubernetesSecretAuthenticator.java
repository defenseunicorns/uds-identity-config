/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.AbstractClientAuthenticator;
import org.keycloak.authentication.authenticators.client.ClientAuthUtil;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.util.BasicAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.Files.readString;

/**
 * Keycloak Client Authenticator that uses `client_id` obtained from the request and `client_secret` obtained from a Kubernetes Secret mounted into the Keycloak Pod.
 *
 * This authenticator validates the client based on the `client_id` and `client_secret` provided in the request. The `client_secret` is retrieved from a Kubernetes Secret mounted into the Keycloak Pod.
 *
 * The authenticator follows these steps:
 * 1. Extracts the `client_id` and `client_secret` from the request headers or form data.
 * 2. Validates the `client_id` and retrieves the corresponding client from the Keycloak session.
 * 3. Checks if the client is enabled and not a public client.
 * 4. Reads the `client_secret` from the mounted Kubernetes Secret.
 * 5. Compares the provided `client_secret` with the mounted `client_secret`. The comparison also use @{code trim} method.
 * 7. Authenticates the client if the secrets match, otherwise reports an authentication failure.
 */
public class ClientIdAndKubernetesSecretAuthenticator extends AbstractClientAuthenticator {

    public static final String PROVIDER_ID = "client-kubernetes-secret";

    public static final String HELP_TOOLTIP_TEXT = "Validates client based on 'client_id' obtained from a client request. The `client_secret` is pulled from a Kubernetes Secret mounted into the Keycloak Pod";

    public static final String DEFAULT_MOUNT_PATH = "/var/run/secrets/uds/client-secrets";
    public static final String DEFAULT_ENVIRONMENT_VARIABLE_CLIENT_MOUNT_PATH = "KC_UDS_CLIENT_SECRET_MOUNT_PATH";

    private static final List<ProviderConfigProperty> clientConfigProperties = new ArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private String secretMountPath;

    static String readMountedClientSecret(String secretMountPath, String clientId) throws IOException {
        String mountedClientSecret = null;
        Path mountedClientSecretPath = Path.of(secretMountPath).resolve(clientId);
        logger.debug("Reading Client Secret from: {}", mountedClientSecretPath);

        mountedClientSecret = readString(mountedClientSecretPath);
        if (mountedClientSecret == null || mountedClientSecret.isEmpty()) {
            throw new IllegalArgumentException("Mounted Client Secret exists but is empty");
        }
        return mountedClientSecret;
    }

    @Override
    public void init(Config.Scope config) {
        secretMountPath = System.getenv(DEFAULT_ENVIRONMENT_VARIABLE_CLIENT_MOUNT_PATH);
        if (secretMountPath == null) {
            secretMountPath = DEFAULT_MOUNT_PATH;
        }
    }

    @Override
    public void authenticateClient(ClientAuthenticationFlowContext context) {
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
                    return;
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
            return;
        }

        context.getEvent().client(client_id);

        ClientModel client = context.getSession().clients().getClientByClientId(context.getRealm(), client_id);
        if (client == null) {
            context.failure(AuthenticationFlowError.CLIENT_NOT_FOUND, null);
            return;
        }

        context.setClient(client);

        if (!client.isEnabled()) {
            context.failure(AuthenticationFlowError.CLIENT_DISABLED, null);
            return;
        }

        if (client.isPublicClient()) {
            context.success();
            return;
        }

        if (clientSecret == null) {
            Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.UNAUTHORIZED.getStatusCode(), "unauthorized_client", "Invalid client or Invalid client credentials");
            context.challenge(challengeResponse);
            return;
        }

        String mountedClientSecret = null;
        try {
            mountedClientSecret = readMountedClientSecret(this.secretMountPath, client_id);
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Client Secret file doesn't exist or is empty, {}", e.getMessage());
            reportMountFileError(context);
            return;
        }

        clientSecret = mountedClientSecret.trim();
        mountedClientSecret = mountedClientSecret.trim();

        if (!clientSecret.equals(mountedClientSecret)) {
            reportFailedAuth(context);
            return;
        }
        context.success();
    }

    @Override
    public String getDisplayType() {
        return "Client Id and Kubernetes Secret";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public String getHelpText() {
        return HELP_TOOLTIP_TEXT;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return clientConfigProperties;
    }

    @Override
    public List<ProviderConfigProperty> getConfigPropertiesPerClient() {
        return clientConfigProperties;
    }

    @Override
    public Map<String, Object> getAdapterConfiguration(ClientModel client) {
        return Collections.emptyMap();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public Set<String> getProtocolAuthenticatorMethods(String loginProtocol) {
        if (loginProtocol.equals(OIDCLoginProtocol.LOGIN_PROTOCOL)) {
            Set<String> results = new LinkedHashSet<>();
            results.add(OIDCLoginProtocol.CLIENT_SECRET_BASIC);
            results.add(OIDCLoginProtocol.CLIENT_SECRET_POST);
            return results;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public boolean supportsSecret() {
        return false;
    }

    private void reportFailedAuth(ClientAuthenticationFlowContext context) {
        Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.UNAUTHORIZED.getStatusCode(), "unauthorized_client", "Invalid client or Invalid client credentials");
        context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, challengeResponse);
    }

    private void reportMountFileError(ClientAuthenticationFlowContext context) {
        context.failure(AuthenticationFlowError.INTERNAL_ERROR, ClientAuthUtil.errorResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "internal_error", "Failed to read client secret"));
    }
}