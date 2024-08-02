package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.keycloak.Config.Scope;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class JSONLogEventListenerProviderFactory implements EventListenerProviderFactory {

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new JSONLogEventListenerProvider();
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
