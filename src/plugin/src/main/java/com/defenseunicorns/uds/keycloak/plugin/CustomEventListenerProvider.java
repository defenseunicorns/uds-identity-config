package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;

public class CustomEventListenerProvider implements EventListenerProvider {

    private final KeycloakSession session;
    private final RealmProvider model;

    public CustomEventListenerProvider(KeycloakSession session) {
        this.session = session;
        this.model = session.realms();
    }

    @Override
    public void onEvent(Event event) {

        if (EventType.REGISTER.equals(event.getType())) {

            RealmModel realm = this.model.getRealm(event.getRealmId());
            UserModel newRegisteredUser = this.session.users().getUserById(realm, event.getUserId());

            newRegisteredUser.setSingleAttribute("newAttribute", "newAttributeValue");
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {

    }

    @Override
    public void close() {

    }
}