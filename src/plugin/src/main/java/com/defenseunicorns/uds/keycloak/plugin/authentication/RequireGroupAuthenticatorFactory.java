/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication;

import java.util.Arrays;
import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class RequireGroupAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "uds-group-restriction";

    public static final RequireGroupAuthenticator GROUP_AUTHENTICATOR = new RequireGroupAuthenticator();

    public static final String TOC_PER_SESSION_CONFIG_NAME = "toc-per-session";

    protected static final List<ProviderConfigProperty> configProperties;

    static {
        ProviderConfigProperty tocPerSession = new ProviderConfigProperty();
        tocPerSession.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        tocPerSession.setName(TOC_PER_SESSION_CONFIG_NAME);
        tocPerSession.setLabel("Display Terms and Conditions only per user session");
        tocPerSession.setDefaultValue(Boolean.toString(true));
        tocPerSession.setHelpText("Setting this to true will display the Terms and Conditions only once per session across multiple devices and applications. Setting this to false will prompt Terms and Conditions on every login.");
        configProperties = Arrays.asList(tocPerSession);
    }

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED
    };

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public Authenticator create(final KeycloakSession session) {
        return GROUP_AUTHENTICATOR;
    }

    @Override
    public void init(final Config.Scope scope) {
    }

    @Override
    public void postInit(final KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getDisplayType() {
        return "UDS Operator Group Authentication Validation";
    }

    @Override
    public String getReferenceCategory() {
        return null;
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
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return null;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

}