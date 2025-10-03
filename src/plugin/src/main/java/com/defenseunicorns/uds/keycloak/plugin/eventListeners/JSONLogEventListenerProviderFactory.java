/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.keycloak.Config.Scope;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class JSONLogEventListenerProviderFactory implements EventListenerProviderFactory {

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new JSONLogEventListenerProvider(session);
    }

    @Override
    public void init(Scope config) {
        // Initialize any configuration if needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post initialization tasks if needed
    }

    @Override
    public void close() {
        // Clean up resources if needed
    }

    @Override
    public String getId() {
        return "jsonlog-event-listener";
    }
}
