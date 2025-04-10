/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.keycloak.events.Errors;
import org.keycloak.models.ClientModel;
import org.keycloak.representations.idm.ClientPolicyExecutorConfigurationRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;
import org.keycloak.services.clientpolicy.executor.FullScopeDisabledExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Keycloak Client Policy Executor for the UDS Operator.
 * <p>
 * The following changes are introduced to the Client:
 * * The Client is marked as created by the UDS Operator by setting the attribute "created-by=uds-operator".
 * * The UDS Operator's Token is checked if it can access particular Client (the client need to contain "created-by=uds-operator" attribute).
 * * The Client can't use the Full Scope Allowed feature.
 * * The Client can use all potential Client Scopes. This will be restricted in the future and is tracked with https://github.com/defenseunicorns/uds-identity-config/issues/385
 */
public class UDSClientPolicyPermissionsExecutor implements ClientPolicyExecutorProvider<ClientPolicyExecutorConfigurationRepresentation> {

    public static final String UDS_OPERATOR_CLIENT_ID = "uds-operator";
    public static final String ATTRIBUTE_UDS_OPERATOR = "created-by";
    public static final String ATTRIBUTE_UDS_OPERATOR_VALUE = "uds-operator";

    private final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void executeOnEvent(ClientPolicyContext context) throws ClientPolicyException {
        if ((context instanceof ClientCRUDContext clientCRUDContext)) {

            logger.debug("Executing UDSClientPolicyPermissionsExecutor, authenticated to Client ID: {}, ", getAuthenticatedClientId(clientCRUDContext));

            if (clientCRUDContext.getAuthenticatedClient() != null && UDS_OPERATOR_CLIENT_ID.equals(getAuthenticatedClientId(clientCRUDContext))) {
                switch (context.getEvent()) {
                    case UPDATE:
                        logger.debug("Updating existing Client with Client ID: {}", clientCRUDContext.getTargetClient().getClientId());
                        if (!isOwnedByUDSOperator(clientCRUDContext.getTargetClient())) {
                            throw new ClientPolicyException(Errors.UNAUTHORIZED_CLIENT, "The Client doesn't have the " + ATTRIBUTE_UDS_OPERATOR + "=" + ATTRIBUTE_UDS_OPERATOR_VALUE + " attribute. Rejecting request.");
                        }
                        enforceClientSettings(clientCRUDContext.getProposedClientRepresentation());
                        break;

                    case REGISTER:
                        logger.debug("Creating new Client with Client ID: {}", clientCRUDContext.getProposedClientRepresentation().getClientId());
                        enforceClientSettings(clientCRUDContext.getProposedClientRepresentation());
                        break;

                    case VIEW:
                    case UNREGISTER:
                        logger.debug("Viewing or deleting Client with Client ID: {}", clientCRUDContext.getTargetClient().getClientId());
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

    @Override
    public void setupConfiguration(ClientPolicyExecutorConfigurationRepresentation config) {
    }

    @Override
    public Class<ClientPolicyExecutorConfigurationRepresentation> getExecutorConfigurationClass() {
        return ClientPolicyExecutorConfigurationRepresentation.class;
    }

    @Override
    public String getProviderId() {
        return FullScopeDisabledExecutorFactory.PROVIDER_ID;
    }

    void enforceClientSettings(ClientRepresentation rep) {
        setUDSOperatorAsOwner(rep);
        setFullScopeDisabled(rep);
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
