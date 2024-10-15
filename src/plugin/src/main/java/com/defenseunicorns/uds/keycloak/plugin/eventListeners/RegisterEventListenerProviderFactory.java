/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class RegisterEventListenerProviderFactory implements EventListenerProviderFactory {
    @Override
    public EventListenerProvider create(KeycloakSession keycloakSession) {
        return new RegisterEventListenerProvider(keycloakSession);
    }

    @Override
    public void init(Config.Scope scope) {
        // no implementation needed
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // no implementation needed
    }

    @Override
    public void close() {
        // no implementation needed
    }

    @Override
    public String getId() {
        return "registration-event-listener";
    }
}
