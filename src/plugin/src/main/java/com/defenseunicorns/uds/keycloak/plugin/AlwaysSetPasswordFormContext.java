/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.core.UriInfo;
import org.keycloak.authentication.FormContext;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Map;

class AlwaysSetPasswordFormContext implements FormContext {
    private final FormContext delegate;
    private final AuthenticatorConfigModel config = new AuthenticatorConfigModel();

    AlwaysSetPasswordFormContext(final FormContext delegate) {
        this.delegate = delegate;
        config.setConfig(Map.of(RegistrationX509Password.ALWAYS_SET_PASSWORD_ON_REGISTER_FORM, "true"));
    }

    @Override
    public EventBuilder getEvent() {
        return delegate.getEvent();
    }

    @Override
    public EventBuilder newEvent() {
        return delegate.newEvent();
    }

    @Override
    public AuthenticationExecutionModel getExecution() {
        return delegate.getExecution();
    }

    @Override
    public UserModel getUser() {
        return delegate.getUser();
    }

    @Override
    public void setUser(final UserModel user) {
        delegate.setUser(user);
    }

    @Override
    public RealmModel getRealm() {
        return delegate.getRealm();
    }

    @Override
    public AuthenticationSessionModel getAuthenticationSession() {
        return delegate.getAuthenticationSession();
    }

    @Override
    public ClientConnection getConnection() {
        return delegate.getConnection();
    }

    @Override
    public UriInfo getUriInfo() {
        return delegate.getUriInfo();
    }

    @Override
    public KeycloakSession getSession() {
        return delegate.getSession();
    }

    @Override
    public HttpRequest getHttpRequest() {
        return delegate.getHttpRequest();
    }

    @Override
    public AuthenticatorConfigModel getAuthenticatorConfig() {
        return config;
    }
}
