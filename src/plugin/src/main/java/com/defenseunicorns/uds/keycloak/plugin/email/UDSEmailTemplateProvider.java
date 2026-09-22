/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.email;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;

import jakarta.ws.rs.core.UriInfo;

import org.keycloak.TokenVerifier;
import org.keycloak.authentication.actiontoken.execactions.ExecuteActionsActionToken;
import org.keycloak.common.VerificationException;
import org.keycloak.email.EmailException;
import org.keycloak.email.freemarker.FreeMarkerEmailTemplateProvider;
import org.keycloak.models.KeycloakSession;

/**
 * Keeps the admin-origin request context intact while generating execute-actions links for the public frontend.
 */
public final class UDSEmailTemplateProvider extends FreeMarkerEmailTemplateProvider {
    private static final String ACTION_TOKEN_PARAMETER = "key";

    private final URI publicHostname;

    public UDSEmailTemplateProvider(KeycloakSession session, URI publicHostname) {
        super(session);
        this.publicHostname = publicHostname;
    }

    @Override
    public void sendExecuteActions(String link, long expirationInMinutes) throws EmailException {
        super.sendExecuteActions(publicExecuteActionsLink(link), expirationInMinutes);
    }

    String publicExecuteActionsLink(String link) throws EmailException {
        if (publicHostname == null || link == null) {
            return link;
        }

        URI linkUri = URI.create(link);
        String serializedToken = queryParameter(linkUri, ACTION_TOKEN_PARAMETER);
        if (serializedToken == null || serializedToken.isBlank()) {
            return link;
        }

        final ExecuteActionsActionToken adminToken;
        try {
            adminToken = TokenVerifier.create(serializedToken, ExecuteActionsActionToken.class).getToken();
        } catch (VerificationException exception) {
            throw new EmailException("Unable to transform execute-actions token", exception);
        }
        ExecuteActionsActionToken publicToken = new ExecuteActionsActionToken(
                adminToken.getUserId(),
                adminToken.getEmail(),
                Math.toIntExact(adminToken.getExp()),
                adminToken.getRequiredActions(),
                adminToken.getRedirectUri(),
                adminToken.getIssuedFor()
        );
        publicToken.id(adminToken.getId());
        publicToken.setCompoundAuthenticationSessionId(adminToken.getCompoundAuthenticationSessionId());
        publicToken.getOtherClaims().putAll(adminToken.getOtherClaims());

        String publicSerializedToken = publicToken.serialize(
                session,
                realm,
                withBaseUri(session.getContext().getUri(), publicHostname)
        );

        int publicPort = normalizedPort(publicHostname);
        String publicHost = publicHostname.getHost();
        if (publicHost != null && publicHost.indexOf(':') >= 0 && !publicHost.startsWith("[")) {
            publicHost = "[" + publicHost + "]";
        }
        StringBuilder publicLink = new StringBuilder()
                .append(publicHostname.getScheme())
                .append("://")
                .append(publicHost)
                .append(publicPort < 0 ? "" : ":" + publicPort)
                .append(linkUri.getRawPath());
        String publicQuery = replaceQueryParameter(
                linkUri.getRawQuery(),
                ACTION_TOKEN_PARAMETER,
                publicSerializedToken
        );
        if (publicQuery != null) {
            publicLink.append('?').append(publicQuery);
        }
        if (linkUri.getRawFragment() != null) {
            publicLink.append('#').append(linkUri.getRawFragment());
        }
        return publicLink.toString();
    }

    private static String queryParameter(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }

        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            String key = separator < 0 ? parameter : parameter.substring(0, separator);
            if (name.equals(key)) {
                return separator < 0 ? "" : parameter.substring(separator + 1);
            }
        }
        return null;
    }

    private static String replaceQueryParameter(String rawQuery, String name, String value) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return name + "=" + value;
        }

        StringBuilder result = new StringBuilder();
        boolean replaced = false;
        for (String parameter : rawQuery.split("&", -1)) {
            int separator = parameter.indexOf('=');
            String key = separator < 0 ? parameter : parameter.substring(0, separator);
            if (name.equals(key)) {
                if (!replaced) {
                    appendParameter(result, name + "=" + value);
                    replaced = true;
                }
            } else {
                appendParameter(result, parameter);
            }
        }
        if (!replaced) {
            appendParameter(result, name + "=" + value);
        }
        return result.toString();
    }

    private static void appendParameter(StringBuilder query, String parameter) {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(parameter);
    }

    private static UriInfo withBaseUri(UriInfo delegate, URI baseUri) {
        return (UriInfo) Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(),
                new Class<?>[]{UriInfo.class},
                (proxy, method, args) -> {
                    if ("getBaseUri".equals(method.getName())) {
                        return baseUri;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    private static int normalizedPort(URI uri) {
        if (("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 443)) {
            return -1;
        }
        return uri.getPort();
    }
}
