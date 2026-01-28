/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.security.cert.X509Certificate;

public class UpdateX509 implements RequiredActionProvider, RequiredActionFactory {
    /**
     * Provider id.
     */
    private static final String PROVIDER_ID = "UPDATE_X509";

    /**
     * Custom implementation.
     */
    @Override
    public void evaluateTriggers(final RequiredActionContext context) {
        String ignore = context.getAuthenticationSession().getAuthNote(Common.IGNORE_X509);
        String x509Username = X509Tools.getX509Username(context);
        if (x509Username == null || ignore != null && ignore.equals("true")) {
            return;
        }

        RealmModel realm = context.getRealm();
        AuthenticationSessionModel authenticationSession = context.getAuthenticationSession();

        X509Certificate[] certAttribute = context.getHttpRequest().getClientCertificateChain();
        String identity = (String) X509Tools.getX509IdentityFromCertChain(certAttribute, realm, authenticationSession);
        context.getUser().setSingleAttribute(Common.USER_ACTIVE_X509_ATTR, identity);

        if (!X509Tools.isX509Registered(context)) {
            context.getUser().addRequiredAction(PROVIDER_ID);
        }
    }

    /**
     * Custom implementation.
     */
    @Override
    public void requiredActionChallenge(final RequiredActionContext context) {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.add(Common.FORM_USERNAME, context.getUser() != null ? context.getUser().getUsername() : "unknown user");
        formData.add("  ", X509Tools.getX509Username(context));
        formData.add(Common.FORM_IS_USER_ENABLED, "true");

        // Prefill CAC-specific optional fields
        CACInfo cac = X509Tools.getCACInfo(context);
        if (cac != null) {
            formData.add(Common.FORM_CAC_SUBJECT_DN, cac.subjectDN());
            formData.add(Common.FORM_CAC_FIRST_NAME, cac.firstName());
            formData.add(Common.FORM_CAC_LAST_NAME, cac.lastName());
            formData.add(Common.FORM_CAC_EMAIL, cac.email());
        }
        context.form().setFormData(formData);

        Response challenge = context.form().createX509ConfirmPage();
        context.challenge(challenge);
    }

    /**
     * Custom implementation.
     */
    @Override
    public void processAction(final RequiredActionContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        if (formData.containsKey("cancel")) {
            context.getAuthenticationSession().setAuthNote(Common.IGNORE_X509, "true");
            context.success();
            return;
        }

        String username = X509Tools.getX509Username(context);
        if (username != null) {
            UserModel user = context.getUser();
            user.setSingleAttribute(Common.USER_X509_ID_ATTRIBUTE, username);
        }

        String commonName = X509Tools.getX509CommonName(context);
        if(commonName != null) {
            UserModel user = context.getUser();
            user.setSingleAttribute(Common.USER_X509_CN_ATTRIBUTE, commonName);
        }
        context.success();
    }

    /**
     * Custom implementation.
     */
    @Override
    public String getDisplayText() {
        return "Update X509";
    }

    /**
     * Custom implementation.
     */
    @Override
    public boolean isOneTimeAction() {
        return true;
    }

    /**
     * Custom implementation.
     */
    @Override
    public RequiredActionProvider create(final KeycloakSession session) {
        return this;
    }

    /**
     * Custom implementation.
     */
    @Override
    public void init(final Config.Scope config) {
        // no implementation needed
    }

    /**
     * Custom implementation.
     */
    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        // no implementation needed
    }

    /**
     * Custom implementation.
     */
    @Override
    public void close() {
        // no implementation needed
    }

    /**
     * Custom implementation.
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

}
