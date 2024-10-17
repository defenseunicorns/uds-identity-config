/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class RequireGroupAuthenticatorFactory implements AuthenticatorFactory {

    /**
     * provider id variable.
     */
    public static final String PROVIDER_ID = "uds-group-restriction";
    /**
     * group authenticator variable.
     */
    public static final RequireGroupAuthenticator GROUP_AUTHENTICATOR = new RequireGroupAuthenticator();
    /**
     * requirement choices variable.
     */
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED
    };

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public Authenticator create(final KeycloakSession session) {
        return GROUP_AUTHENTICATOR;
    }

    @Override
    public void init(final Config.Scope scope) {
        // no implementation needed here
    }

    @Override
    public void postInit(final KeycloakSessionFactory keycloakSessionFactory) {
        // no implementation needed here
    }

    @Override
    public void close() {
        // no implementation needed here
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public String getDisplayType() {
        return "UDS Operator Group Authentication Validation";
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public String getReferenceCategory() {
        return null;
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public boolean isConfigurable() {
        return false;
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public String getHelpText() {
        return null;
    }

    /**
     * This implementation is not intended to be overridden.
     */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // no implementation needed here. Just return empty collection
        return new ArrayList<>();
    }
}