package com.defenseunicorns.uds.keycloak.plugin;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

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

            if(!newRegisteredUser.getAttributes().containsKey(Common.USER_MATTERMOST_ID_ATTR)) {
                generateMattermostId(newRegisteredUser);
            }
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {
        // no implementation needed
    }

    @Override
    public void close() {
        // no implementation needed
    }

    /**
     * Add a custom user attribute (mattermostid) to enable direct mattermost <>
     * keycloak auth on mattermost teams edition.
     *
     * @param user     the Keycloak user object
     */
    private static void generateMattermostId(UserModel user) {
        if (user != null && user.getEmail() != null){
            String email = user.getEmail();

            byte[] encodedEmail;
            int emailByteTotal = 0;
            Date today = new Date();
    
            encodedEmail = email.getBytes(StandardCharsets.US_ASCII);
            for (byte b : encodedEmail) {
                emailByteTotal += b;
            }
    
            SimpleDateFormat formatDate = new SimpleDateFormat("yyDHmsS");
    
            user.setSingleAttribute(Common.USER_MATTERMOST_ID_ATTR, formatDate.format(today) + emailByteTotal);
        }
    }
}
