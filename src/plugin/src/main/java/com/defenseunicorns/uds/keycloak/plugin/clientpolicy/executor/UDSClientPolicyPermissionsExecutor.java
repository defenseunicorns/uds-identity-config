/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.defenseunicorns.uds.keycloak.plugin.CustomAWSSAMLGroupMapper;
import com.defenseunicorns.uds.keycloak.plugin.CustomGroupPathMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.keycloak.common.util.CollectionUtil;
import org.keycloak.events.Errors;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.mappers.*;
import org.keycloak.protocol.saml.mappers.RoleListMapper;
import org.keycloak.protocol.saml.mappers.UserAttributeStatementMapper;
import org.keycloak.protocol.saml.mappers.UserPropertyAttributeStatementMapper;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;
import org.keycloak.services.clientpolicy.executor.FullScopeDisabledExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.keycloak.common.util.CollectionUtil.*;

/**
 * Keycloak Client Policy Executor for the UDS Operator.
 * <p>
 * The following changes are introduced to the Client:
 * * The Client is marked as created by the UDS Operator by setting the attribute "created-by=uds-operator".
 * * The UDS Operator's Token is checked if it can access particular Client (the client need to contain "created-by=uds-operator" attribute).
 * * The Client can't use the Full Scope Allowed feature.
 * * The Client can only use the Protocol Mappers that are whitelisted in the configuration. The default, opinionated configuration uses OIDC nad UDS Protocol Mappers.
 * * The Client can only use the Custom Client Scopes that are whitelisted in the configuration. The default, opinionated configuration uses UDS Provided Client Scopes, Client Scopes configured at the Realm level as well as SAML defaults.
 */
public class UDSClientPolicyPermissionsExecutor implements ClientPolicyExecutorProvider<UDSClientPolicyPermissionsExecutorConfiguration> {

    public static final String UDS_OPERATOR_CLIENT_ID = "uds-operator";
    public static final String ATTRIBUTE_UDS_OPERATOR = "created-by";
    public static final String ATTRIBUTE_UDS_OPERATOR_VALUE = "uds-operator";

    public static final List<String> DEFAULT_ALLOWED_PROTOCOL_MAPPERS = List.of(
            UserAttributeStatementMapper.PROVIDER_ID,
            UserAttributeMapper.PROVIDER_ID,
            UserPropertyAttributeStatementMapper.PROVIDER_ID,
            UserPropertyMapper.PROVIDER_ID,
            FullNameMapper.PROVIDER_ID,
            AddressMapper.PROVIDER_ID,
            new SHA256PairwiseSubMapper().getId(),
            RoleListMapper.PROVIDER_ID,
            CustomAWSSAMLGroupMapper.PROVIDER_ID
    );

    public static final List<String> DEFAULT_ALLOWED_CLIENT_SCOPES = List.of(
            "openid",
            "aws-groups",
            CustomGroupPathMapper.GROUPS_CLAIM,
            CustomGroupPathMapper.BARE_GROUPS_CLAIM,
            "mapper-saml-username-name",
            "mapper-saml-email-email",
            "mapper-saml-username-login",
            "mapper-saml-firstname-first_name",
            "mapper-saml-lastname-last_name",
            "mapper-saml-grouplist-groups",
            "mapper-oidc-username-username",
            "mapper-oidc-mattermostid-id",
            "mapper-oidc-email-email"
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

    @Override
    public void executeOnEvent(ClientPolicyContext context) throws ClientPolicyException {
        if ((context instanceof ClientCRUDContext clientCRUDContext)) {
            logger.debug("Executing UDSClientPolicyPermissionsExecutor, authenticated to Client ID: {}, ", getAuthenticatedClientId(clientCRUDContext));

            if (clientCRUDContext.getAuthenticatedClient() != null && UDS_OPERATOR_CLIENT_ID.equals(getAuthenticatedClientId(clientCRUDContext))) {
                switch (context.getEvent()) {
                    case UPDATE:
                        logger.trace("Updating existing Client with Client ID: {}", clientCRUDContext.getTargetClient().getClientId());
                        if (!isOwnedByUDSOperator(clientCRUDContext.getTargetClient())) {
                            throw new ClientPolicyException(Errors.UNAUTHORIZED_CLIENT, "The Client doesn't have the " + ATTRIBUTE_UDS_OPERATOR + "=" + ATTRIBUTE_UDS_OPERATOR_VALUE + " attribute. Rejecting request.");
                        }
                        enforceClientSettings(clientCRUDContext.getProposedClientRepresentation());
                        break;

                    case REGISTER:
                        logger.trace("Creating new Client with Client ID: {}", clientCRUDContext.getProposedClientRepresentation().getClientId());
                        enforceClientSettings(clientCRUDContext.getProposedClientRepresentation());
                        break;

                    case VIEW:
                    case UNREGISTER:
                        logger.trace("Viewing or deleting Client with Client ID: {}", clientCRUDContext.getTargetClient().getClientId());
                        if (!isOwnedByUDSOperator(clientCRUDContext.getTargetClient())) {
                            throw new ClientPolicyException(Errors.UNAUTHORIZED_CLIENT, "The Client doesn't have the " + ATTRIBUTE_UDS_OPERATOR + "=" + ATTRIBUTE_UDS_OPERATOR_VALUE + " attribute. Rejecting request.");
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    String getAuthenticatedClientId(ClientCRUDContext clientCRUDContext) {
        if (clientCRUDContext.getAuthenticatedClient() == null) {
            return null;
        }
        return clientCRUDContext.getAuthenticatedClient().getClientId();
    }

    boolean isOwnedByUDSOperator(ClientModel client) {
        return ATTRIBUTE_UDS_OPERATOR_VALUE.equals(client.getAttribute(ATTRIBUTE_UDS_OPERATOR));
    }

    void setAllowedProtocolMappers(ClientRepresentation rep) {
        if (isNotEmpty(rep.getProtocolMappers()) && configuration.getAllowedProtocolMappers() != null) {
            rep.getProtocolMappers()
                    .removeIf(mapper -> !configuration.getAllowedProtocolMappers().contains(mapper.getProtocolMapper()));
        }
    }

    void setAllowedCustomClientScopes(ClientRepresentation rep) {
        if (configuration.getAllowedClientScopes() != null) {
            if (isNotEmpty(rep.getDefaultClientScopes())) {
                rep.getDefaultClientScopes()
                        .removeIf(scope -> !configuration.getAllowedClientScopes().contains(scope));
            }
            if (isNotEmpty(rep.getOptionalClientScopes())) {
                rep.getOptionalClientScopes()
                        .removeIf(scope -> !configuration.getAllowedClientScopes().contains(scope));
            }
        }
    }

    @Override
    public void setupConfiguration(UDSClientPolicyPermissionsExecutorConfiguration config) {
        if (config.isUseDefaultAllowedProtocolMappers()) {
            List<String> defenseUnicornsProtocolMappers = this.keycloakSession.getKeycloakSessionFactory().
                    getProviderFactoriesStream(ProtocolMapper.class)
                    .filter(o -> o.getClass().getPackageName().startsWith("com.defenseunicorns.uds.keycloak"))
                    .map(o -> o.getId())
                    .toList();

            List<String> allowedProtocolMappers = new ArrayList<>();
            allowedProtocolMappers.addAll(DEFAULT_ALLOWED_PROTOCOL_MAPPERS);
            allowedProtocolMappers.addAll(defenseUnicornsProtocolMappers);
            if (config.getAllowedProtocolMappers() != null)
                allowedProtocolMappers.addAll(config.getAllowedProtocolMappers());
            config.setAllowedProtocolMappers(allowedProtocolMappers);
        }

        if (config.isUseDefaultAllowedClientScopes()) {
            List<String> allowedClientScopes = new ArrayList<>();
            allowedClientScopes.addAll(DEFAULT_ALLOWED_CLIENT_SCOPES);
            // Add Realm defaults
            allowedClientScopes.addAll(this.keycloakSession.getContext().getRealm().getDefaultClientScopesStream(true).map(ClientScopeModel::getName).collect(Collectors.toList()));
            if (config.getAllowedClientScopes() != null)
                allowedClientScopes.addAll(config.getAllowedClientScopes());
            config.setAllowedClientScopes(allowedClientScopes);
        }

        logger.debug("Initializing with configuration: {}", config);
        this.configuration = config;
    }

    @Override
    public Class<UDSClientPolicyPermissionsExecutorConfiguration> getExecutorConfigurationClass() {
        return UDSClientPolicyPermissionsExecutorConfiguration.class;
    }

    @Override
    public String getProviderId() {
        return FullScopeDisabledExecutorFactory.PROVIDER_ID;
    }

    void enforceClientSettings(ClientRepresentation rep) {
        logger.trace("Client Representation before enforcements: {}", toString(rep));

        setUDSOperatorAsOwner(rep);
        setFullScopeDisabled(rep);
        setAllowedProtocolMappers(rep);
        setAllowedCustomClientScopes(rep);

        logger.trace("Client Representation after enforcements: {}", toString(rep));
    }

    private void setUDSOperatorAsOwner(ClientRepresentation rep) {
        if (rep.getAttributes() == null)
            rep.setAttributes(new java.util.HashMap<>());
        rep.getAttributes().put(ATTRIBUTE_UDS_OPERATOR, ATTRIBUTE_UDS_OPERATOR_VALUE);
    }

    private void setFullScopeDisabled(ClientRepresentation rep) {
        rep.setFullScopeAllowed(false);
    }
}
