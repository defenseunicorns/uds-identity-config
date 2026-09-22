/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.email;

import java.net.URI;

import org.keycloak.Config;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.email.EmailTemplateProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public final class UDSEmailTemplateProviderFactory implements EmailTemplateProviderFactory {
    private URI publicHostname;

    @Override
    public EmailTemplateProvider create(KeycloakSession session) {
        return new UDSEmailTemplateProvider(session, publicHostname);
    }

    @Override
    public void init(Config.Scope config) {
        String configuredHostname = config.get("hostname");
        if (configuredHostname == null) {
            configuredHostname = System.getenv("KC_HOSTNAME");
        }
        if (configuredHostname != null) {
            publicHostname = toUri(configuredHostname);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "freemarker";
    }

    @Override
    public int order() {
        return 1;
    }

    private static URI toUri(String value) {
        String normalized = value.endsWith("/") ? value : value + "/";
        URI uri = URI.create(normalized);
        if (uri.getScheme() == null) {
            if (isPlainHostname(value)) {
                // Keycloak resolves the scheme, port, and context path for host-only hostname values.
                // Leave rewriting disabled so we do not guess any of those components here.
                return null;
            }
            throw new IllegalArgumentException("Provided hostname is not a valid URL: " + value);
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Provided hostname is not a valid URL: " + value);
        }
        return uri;
    }

    private static boolean isPlainHostname(String value) {
        URI uri = URI.create("https://" + value);
        return value.equals(uri.getHost());
    }
}
