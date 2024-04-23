package com.defenseunicorns.uds.keycloak.plugin;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.keycloak.Config.Scope;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;

public class CustomUserAttributes implements RequiredActionProvider, RequiredActionFactory {
    /**
     * Provider id.
     */
    private static final String PROVIDER_ID = "CUSTOM_USER_ATTRIBUTES";

    /**
     * Custom implementation.
     */
    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        if (context.getUser() != null && context.getUser().getEmail() != null) {
            context.getUser().addRequiredAction(getId());
        }
    }

    /**
     * Custom implementation.
     */
    @Override
    public void processAction(RequiredActionContext context) {
        generateMattermostId(context.getUser());

        context.success();
    }

    /**
     * Add a custom user attribute (mattermostid) to enable direct mattermost <>
     * keycloak auth on mattermost teams edition.
     *
     * @param user     the Keycloak user object
     */
    private static void generateMattermostId(UserModel user) {
        String email = user.getEmail();

        byte[] encodedEmail;
        int emailByteTotal = 0;
        Date today = new Date();

        encodedEmail = email.getBytes(StandardCharsets.US_ASCII);
        for (byte b : encodedEmail) {
            emailByteTotal += b;
        }

        SimpleDateFormat formatDate = new SimpleDateFormat("yyDHmsS");

        user.setSingleAttribute("mattermostid", formatDate.format(today) + emailByteTotal);
    }

    /**
     * Custom implementation.
     */
    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        processAction(context);
    }

    /**
     * Custom implementation.
     */
    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    /**
     * Custom implementation.
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * Custom implementation.
     */
    @Override
    public String getDisplayText() {
        return "Custom User Attributes";
    }

    @Override
    public void init(Scope config) {
        // no implementation needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // no implementation needed
    }

    @Override
    public void close() {
        // no implementation needed
    }
}