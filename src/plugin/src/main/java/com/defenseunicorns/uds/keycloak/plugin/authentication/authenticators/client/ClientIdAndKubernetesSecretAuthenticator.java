/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.AbstractClientAuthenticator;
import org.keycloak.authentication.authenticators.client.ClientAuthUtil;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.Files.readString;

public class ClientIdAndKubernetesSecretAuthenticator extends AbstractClientAuthenticator {

    public static final String PROVIDER_ID = "client-kubernetes-secret";

    public static final String HELP_TOOLTIP_TEXT = "Validates client based on 'client_id' and 'client_secret' sent either in request parameters or in 'Authorization: Basic' header. The `client_secret` is pulled from a Kubernetes Secret mounted into the Keycloak Pod";

    public static final String DEFAULT_MOUNT_PATH = "/var/run/secrets/uds/client-secrets";
    public static final String DEFAULT_ENVIRONMENT_VARIABLE_CLIENT_MOUNT_PATH = "KC_UDS_CLIENT_SECRET_MOUNT_PATH";

    private static final List<ProviderConfigProperty> clientConfigProperties = new ArrayList<>();
    private final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
        ClientAuthenticatorUtils.ClientAuthenticatorData clientAuthenticationData = ClientAuthenticatorUtils.getExtractClientIdAndSecret(context);
        if (clientAuthenticationData == ClientAuthenticatorUtils.INVALID) {
            logger.debug("Authentication failed: {}", context);
            return;
        }

        String mountedClientSecret = null;
        try {
            mountedClientSecret = readMountedClientSecret(this.secretMountPath, clientAuthenticationData.client_id());
        } catch (IOException | IllegalArgumentException e) {
            logger.debug("Client Secret file doesn't exist or is empty, {}", e.getMessage());
            reportMountFileError(context);
            return;
        }

        if (!clientAuthenticationData.clientSecret().equals(mountedClientSecret)) {
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