/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-DefenseUnicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

/** Runs the persisted frontend URL cleanup once after Keycloak database migrations. */
public final class UDSFrontendUrlMigrationProviderFactory
        implements EventListenerProviderFactory, ProviderEventListener {
    private static final Logger LOG = Logger.getLogger(UDSFrontendUrlMigrationProviderFactory.class);
    private String domain;

    public UDSFrontendUrlMigrationProviderFactory() {
    }

    UDSFrontendUrlMigrationProviderFactory(final String domain) {
        this.domain = domain;
    }

    @Override
    public EventListenerProvider create(final KeycloakSession session) {
        return new EventListenerProvider() {
            @Override
            public void onEvent(final org.keycloak.events.Event event) {
                // This factory listens to provider lifecycle events, not realm events.
            }

            @Override
            public void onEvent(final org.keycloak.events.admin.AdminEvent event, final boolean includeRepresentation) {
                // This factory listens to provider lifecycle events, not admin events.
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void init(final Config.Scope config) {
        domain = System.getenv("UDS_DOMAIN");
    }

    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        factory.register(this);
    }

    @Override
    public void onEvent(final ProviderEvent event) {
        if (event instanceof PostMigrationEvent postMigrationEvent) {
            KeycloakModelUtils.runJobInTransaction(postMigrationEvent.getFactory(), this::migrate);
        }
    }

    private void migrate(final KeycloakSession session) {
        RealmModel realm = session.realms().getRealmByName("uds");
        if (realm == null || !UDSFrontendUrlMigration.isUDSRealm(realm)) {
            LOG.warn("Could not find the uds realm for the Identity Config 0.32.1 frontendUrl migration");
            return;
        }

        UDSFrontendUrlMigration.Result result = UDSFrontendUrlMigration.migrate(realm, domain);
        switch (result) {
            case REMOVED_GENERATED_VALUE -> LOG.info("Removed the Identity Config 0.32.1 generated uds.frontendUrl");
            case REVIEW_MISSING_DOMAIN -> LOG.warnf(
                    "Preserved uds.frontendUrl '%s': UDS_DOMAIN is unavailable; review this realm value manually",
                    realm.getAttribute(UDSFrontendUrlMigration.FRONTEND_URL_ATTRIBUTE));
            case REVIEW_VALUE_MISMATCH -> LOG.warnf(
                    "Preserved uds.frontendUrl '%s': it does not match the current UDS_DOMAIN; review this realm value manually",
                    realm.getAttribute(UDSFrontendUrlMigration.FRONTEND_URL_ATTRIBUTE));
            case NOTHING_TO_REMOVE -> LOG.info("No uds.frontendUrl value was set during the Identity Config 0.32.1 migration");
            case SKIPPED_ALREADY_COMPLETE -> LOG.debug("The Identity Config 0.32.1 frontendUrl migration already ran");
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "uds-frontend-url-migration";
    }
}
