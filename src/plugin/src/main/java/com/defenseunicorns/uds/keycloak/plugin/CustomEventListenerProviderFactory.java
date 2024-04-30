package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class CustomEventListenerProviderFactory implements EventListenerProviderFactory {
    @Override
    public EventListenerProvider create(KeycloakSession keycloakSession) {
        return new CustomEventListenerProvider(keycloakSession);
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
        return "custom-registration-listener";
    }
}
