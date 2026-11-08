/*
 * Copyright 2024-2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.defenseunicorns.uds.keycloak.plugin.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.events.Errors;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.keycloak.services.clientpolicy.context.admin.ClientProtocolMapperContext;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;

/**
 * Keycloak Client Policy Executor for UDS-managed admin clients (e.g. uds-operator, uds-fleet-admin).
 * <p>
 * The executor only acts when the request is authenticated as one of the managed clients
 * ({@link #MANAGED_CLIENT_ID_PREFIXES}). For those clients the following rules apply:
 * * Clients created by a managed client are stamped with "created-by=&lt;authenticated clientId&gt;".
 * * A managed client may only update/view/delete clients it created (matching "created-by" attribute).
 * * A managed client may only create clients whose clientId starts with its required prefix (empty = no constraint).
 * * The Client can only use the Protocol Mappers that are whitelisted in the configuration. The default, opinionated configuration uses OIDC and UDS Protocol Mappers.
 * * The Client can only use the Custom Client Scopes that are whitelisted in the configuration. The default, opinionated configuration uses UDS Provided Client Scopes, Client Scopes configured at the Realm level as well as SAML defaults.
 */
public class UDSClientPolicyPermissionsExecutor implements ClientPolicyExecutorProvider<UDSClientPolicyPermissionsExecutorConfiguration> {

    public static final String ATTRIBUTE_CREATED_BY = "created-by";

    /**
     * Clients governed by this executor, mapped to the clientId prefix required for clients they create.
     * The authenticated clientId is still stamped directly into created-by; this map only acts as the
     * managed-client allowlist and per-client prefix configuration. An empty prefix imposes no naming
     * constraint (preserves uds-operator's existing behavior).
     */
    public static final Map<String, String> MANAGED_CLIENT_ID_PREFIXES = Map.of(
            "uds-operator", "",
            "uds-fleet-admin", "fleet-"
    );

    protected final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected final KeycloakSession keycloakSession;
    protected UDSClientPolicyPermissionsExecutorConfiguration configuration;

    public UDSClientPolicyPermissionsExecutor(KeycloakSession session) {
        this.keycloakSession = session;
    }

    private static String toString(ClientRepresentation rep) {
        try {
            return new ObjectMapper().writeValueAsString(rep);
        } catch (Exception e) {
            return "Failed to convert ClientRepresentation to JSON, " + e.getMessage();
        }
    }

    /** Routes client and mapper events while retaining existing managed-client and ownership checks. */
    @Override
    public void executeOnEvent(ClientPolicyContext context) throws ClientPolicyException {
        if (context instanceof ClientProtocolMapperContext mapperContext) {
            validateClientProtocolMapper(mapperContext);
        }
        if ((context instanceof ClientCRUDContext clientCRUDContext)) {
            String authenticatedClientId = getAuthenticatedClientId(clientCRUDContext);
            boolean hasAuthenticatedClient = clientCRUDContext.getAuthenticatedClient() != null;
            boolean managedClient = isManagedClient(authenticatedClientId);
            String requiredClientIdPrefix = getRequiredClientIdPrefix(authenticatedClientId);
            logger.debug(
                    "Executing UDSClientPolicyPermissionsExecutor, event: {}, hasAuthenticatedClient: {}, authenticatedClientId: {}, managedClient: {}, requiredClientIdPrefix: {}",
                    context.getEvent(),
                    hasAuthenticatedClient,
                    authenticatedClientId,
                    managedClient,
                    requiredClientIdPrefix
            );

            if (hasAuthenticatedClient && managedClient) {
                ClientModel targetClient = clientCRUDContext.getTargetClient();
                ClientRepresentation proposedClient = clientCRUDContext.getProposedClientRepresentation();
                logger.debug(
                        "Applying UDSClientPolicyPermissionsExecutor, event: {}, authenticatedClientId: {}, requiredClientIdPrefix: {}, targetClientId: {}, proposedClientId: {}",
                        context.getEvent(),
                        authenticatedClientId,
                        requiredClientIdPrefix,
                        targetClient != null ? targetClient.getClientId() : null,
                        proposedClient != null ? proposedClient.getClientId() : null
                );

                switch (context.getEvent()) {
                    case UPDATE:
                        logger.trace("Updating existing Client with Client ID: {}", clientCRUDContext.getTargetClient().getClientId());
                        ensureOwnedBy(clientCRUDContext.getTargetClient(), authenticatedClientId);
                        boolean retainAdminApiScope = ClientScopePolicy.hasAdminApiScope(clientCRUDContext.getTargetClient());
                        validateClientSettings(clientCRUDContext.getProposedClientRepresentation(), retainAdminApiScope);
                        validateClientIdPrefix(clientCRUDContext.getProposedClientRepresentation(), authenticatedClientId);
                        setOwner(clientCRUDContext.getProposedClientRepresentation(), authenticatedClientId);
                        break;

                    case REGISTER:
                        logger.trace("Creating new Client with Client ID: {}", clientCRUDContext.getProposedClientRepresentation().getClientId());
                        validateClientSettings(clientCRUDContext.getProposedClientRepresentation());
                        validateClientIdPrefix(clientCRUDContext.getProposedClientRepresentation(), authenticatedClientId);
                        setOwner(clientCRUDContext.getProposedClientRepresentation(), authenticatedClientId);
                        break;

                    case VIEW:
                    case UNREGISTER:
                        logger.trace("Viewing or deleting Client with Client ID: {}", clientCRUDContext.getTargetClient().getClientId());
                        ensureOwnedBy(clientCRUDContext.getTargetClient(), authenticatedClientId);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** Applies mapper rules only to callers already governed by this executor. */
    private void validateClientProtocolMapper(ClientProtocolMapperContext context) throws ClientPolicyException {
        if (!isManagedClient(getAuthenticatedClientId(context.getAuthenticatedClient()))) {
            return;
        }
        ProtocolMapperPolicy.validateEvent(context, configuration.getAllowedProtocolMappers());
    }

    /** Returns the authenticated client ID, or null for non-client authentication. */
    private String getAuthenticatedClientId(ClientModel authenticatedClient) {
        return authenticatedClient == null ? null : authenticatedClient.getClientId();
    }

    private void ensureOwnedBy(ClientModel client, String authenticatedClientId) throws ClientPolicyException {
        if (!isOwnedBy(client, authenticatedClientId)) {
            throw new ClientPolicyException(Errors.UNAUTHORIZED_CLIENT, "The Client doesn't have the " + ATTRIBUTE_CREATED_BY + "=" + authenticatedClientId + " attribute. Rejecting request.");
        }
    }

    String getAuthenticatedClientId(ClientCRUDContext clientCRUDContext) {
        if (clientCRUDContext.getAuthenticatedClient() == null) {
            return null;
        }
        return clientCRUDContext.getAuthenticatedClient().getClientId();
    }

    boolean isOwnedBy(ClientModel client, String ownerClientId) {
        return ownerClientId.equals(client.getAttribute(ATTRIBUTE_CREATED_BY));
    }

    boolean isManagedClient(String clientId) {
        return clientId != null && MANAGED_CLIENT_ID_PREFIXES.containsKey(clientId);
    }

    String getRequiredClientIdPrefix(String clientId) {
        return clientId == null ? null : MANAGED_CLIENT_ID_PREFIXES.get(clientId);
    }

    void validateClientIdPrefix(ClientRepresentation rep, String authenticatedClientId) throws ClientPolicyException {
        String requiredPrefix = getRequiredClientIdPrefix(authenticatedClientId);
        if (StringUtils.isNotBlank(requiredPrefix)) {
            String clientId = rep.getClientId();
            if (clientId == null || !clientId.startsWith(requiredPrefix)) {
                throw new ClientPolicyException(Errors.INVALID_CLIENT, "The Client ID must start with \"" + requiredPrefix + "\". Rejecting request.");
            }
        }
    }

    /** Resolves configured allowances once for this executor without mutating realm delegation. */
    @Override
    public void setupConfiguration(UDSClientPolicyPermissionsExecutorConfiguration config) {
        config.setAllowedProtocolMappers(ProtocolMapperPolicy.resolveAllowed(config, keycloakSession));
        config.setAllowedClientScopes(ClientScopePolicy.resolveAllowed(config, keycloakSession));
        logger.debug("Initializing with configuration: {}", config);
        this.configuration = config;
    }

    @Override
    public Class<UDSClientPolicyPermissionsExecutorConfiguration> getExecutorConfigurationClass() {
        return UDSClientPolicyPermissionsExecutorConfiguration.class;
    }

    @Override
    public String getProviderId() {
        return UDSClientPolicyPermissionsExecutorFactory.PROVIDER_ID;
    }

    /** Validates registration without the existing-default-scope retention exception. */
    void validateClientSettings(ClientRepresentation rep) throws ClientPolicyException {
        validateClientSettings(rep, false);
    }

    /** Validates mapper rules before scopes, with retention authorized by the caller. */
    private void validateClientSettings(ClientRepresentation rep, boolean retainAdminApiScope) throws ClientPolicyException {
        logger.trace("Validating Client Representation before enforcements: {}", toString(rep));

        ProtocolMapperPolicy.validate(rep.getProtocolMappers(), configuration.getAllowedProtocolMappers());
        ClientScopePolicy.validate(rep, configuration.getAllowedClientScopes(), retainAdminApiScope);

        logger.trace("Client Representation after enforcements: {}", toString(rep));
    }

    private void setOwner(ClientRepresentation rep, String ownerClientId) {
        if (rep.getAttributes() == null)
            rep.setAttributes(new java.util.HashMap<>());
        rep.getAttributes().put(ATTRIBUTE_CREATED_BY, ownerClientId);
    }
}
