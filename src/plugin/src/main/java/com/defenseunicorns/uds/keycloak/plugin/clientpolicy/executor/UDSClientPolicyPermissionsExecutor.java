/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.keycloak.events.Errors;
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

public class UDSClientPolicyPermissionsExecutor implements ClientPolicyExecutorProvider<UDSClientPolicyPermissionsExecutor.Configuration> {

    public static final String ATTRIBUTE_UDS_OPERATOR = "created-by";
    public static final String ATTRIBUTE_UDS_OPERATOR_VALUE = "uds-operator";
    private final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Configuration configuration;

    @Override
    public void executeOnEvent(ClientPolicyContext context) throws ClientPolicyException {
        logger.debug("executeOnEvent: {}", context);
        switch (context.getEvent()) {
            case REGISTER:
            case UPDATE:
                logger.debug("executeOnEvent.REGISTER/UPDATE: {}", context);
                ClientCRUDContext clientUpdateContext = (ClientCRUDContext) context;
                autoConfigure(clientUpdateContext.getProposedClientRepresentation());
                validate(clientUpdateContext.getProposedClientRepresentation());
                break;
            case VIEW:
                logger.debug("executeOnEvent.VIEW: {}", context);
                ClientCRUDContext clientViewContext = (ClientCRUDContext) context;
                logger.debug("Authenticated Client: {}", clientViewContext.getAuthenticatedClient());
                logger.debug("Authenticated User: {}", clientViewContext.getAuthenticatedUser());
                break;
        }
    }

    @Override
    public void setupConfiguration(Configuration config) {
        this.configuration = config;
    }

    @Override
    public Class<Configuration> getExecutorConfigurationClass() {
        return Configuration.class;
    }

    @Override
    public String getProviderId() {
        return FullScopeDisabledExecutorFactory.PROVIDER_ID;
    }

    private void autoConfigure(ClientRepresentation rep) {
        if (configuration.isAutoConfigure()) {
            if (rep.getAttributes() == null)
                rep.setAttributes(new java.util.HashMap<>());
            rep.getAttributes().put(ATTRIBUTE_UDS_OPERATOR, ATTRIBUTE_UDS_OPERATOR_VALUE);
            rep.setFullScopeAllowed(false);
        }
    }

    private void validate(ClientRepresentation proposedClient) throws ClientPolicyException {
        if (proposedClient.isFullScopeAllowed() != null && proposedClient.isFullScopeAllowed()) {
            throw new ClientPolicyException(Errors.INVALID_REGISTRATION, "Not permitted to enable fullScopeAllowed");
        }
    }

    public static class Configuration extends ClientPolicyExecutorConfigurationRepresentation {
        @JsonProperty("auto-configure")
        protected Boolean autoConfigure;

        public Boolean isAutoConfigure() {
            return autoConfigure;
        }

        public void setAutoConfigure(Boolean autoConfigure) {
            this.autoConfigure = autoConfigure;
        }
    }
}
