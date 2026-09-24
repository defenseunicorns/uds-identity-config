/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

/**
 * Reconciles the realm frontend URLs used by Keycloak's native hostname provider.
 *
 * <p>The UDS and master realms intentionally use different public origins. The
 * values are reconciled after migrations and after a realm is created so a fresh
 * install, an upgrade, and a changed domain all converge without an admin API
 * step.</p>
 */
public final class UDSRealmFrontendUrlProviderFactory
        implements EventListenerProviderFactory, ProviderEventListener {
    private static final Logger LOG = Logger.getLogger(UDSRealmFrontendUrlProviderFactory.class);
    private static final String FRONTEND_URL = "frontendUrl";
    private static final String MASTER_REALM = "master";
    private static final String UDS_REALM = "uds";

    private String publicFrontendUrl;
    private String adminFrontendUrl;

    UDSRealmFrontendUrlProviderFactory(String domain, String adminDomain) {
        configure(domain, adminDomain);
    }

    public UDSRealmFrontendUrlProviderFactory() {
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new EventListenerProvider() {
            @Override
            public void onEvent(Event event) {
                // This factory is registered for ProviderEvent lifecycle hooks, not realm event delivery.
            }

            @Override
            public void onEvent(AdminEvent event, boolean includeRepresentation) {
                // This factory is registered for ProviderEvent lifecycle hooks, not realm event delivery.
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void init(Config.Scope config) {
        configure(System.getenv("UDS_DOMAIN"), System.getenv("UDS_ADMIN_DOMAIN"));
    }

    private void configure(String domain, String adminDomain) {
        publicFrontendUrl = frontendUrl("sso", domain);
        adminFrontendUrl = frontendUrl("keycloak", adminDomain);

        if (publicFrontendUrl == null || adminFrontendUrl == null) {
            LOG.warn("UDS_DOMAIN and UDS_ADMIN_DOMAIN must both be set to reconcile realm frontend URLs");
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(this);
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "uds-realm-frontend-url";
    }

    @Override
    public void onEvent(ProviderEvent event) {
        if (event instanceof RealmModel.RealmPostCreateEvent realmPostCreateEvent) {
            reconcile(realmPostCreateEvent.getKeycloakSession(), realmPostCreateEvent.getCreatedRealm());
        } else if (event instanceof PostMigrationEvent postMigrationEvent) {
            KeycloakModelUtils.runJobInTransaction(postMigrationEvent.getFactory(), this::reconcileRealms);
        }
    }

    private void reconcileRealms(KeycloakSession session) {
        reconcile(session, session.realms().getRealmByName(MASTER_REALM));
        reconcile(session, session.realms().getRealmByName(UDS_REALM));
    }

    private void reconcile(KeycloakSession session, RealmModel realm) {
        if (session == null || realm == null) {
            return;
        }

        String desiredFrontendUrl = desiredFrontendUrl(realm.getName());
        if (desiredFrontendUrl != null && !desiredFrontendUrl.equals(realm.getAttribute(FRONTEND_URL))) {
            realm.setAttribute(FRONTEND_URL, desiredFrontendUrl);
            LOG.infof("Set frontend URL for realm %s to %s", realm.getName(), desiredFrontendUrl);
        }
    }

    private String desiredFrontendUrl(String realmName) {
        return switch (realmName) {
            case MASTER_REALM -> adminFrontendUrl;
            case UDS_REALM -> publicFrontendUrl;
            default -> null;
        };
    }

    private static String frontendUrl(String prefix, String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return "https://" + prefix + "." + domain.trim();
    }
}
