/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.email;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.Token;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProviderFactory;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakUriInfo;
import org.keycloak.models.RealmModel;
import org.keycloak.models.TokenManager;
import org.keycloak.authentication.actiontoken.execactions.ExecuteActionsActionToken;
import org.keycloak.util.JsonSerialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UDSEmailTemplateProviderTest {
    private static final URI ADMIN_URL = URI.create("https://keycloak.admin.uds.dev/");
    private static final URI PUBLIC_URL = URI.create("https://sso.uds.dev/");
    private static final String REALM_NAME = "uds";
    private static final String USER_ID = "user-id";
    private static final String EMAIL = "user@example.test";
    private static final String CLIENT_ID = "account";

    @BeforeAll
    static void configureJaxRsRuntime() {
        RuntimeDelegate runtimeDelegate = mock(RuntimeDelegate.class);
        UriBuilder uriBuilder = mock(UriBuilder.class);
        AtomicReference<URI> baseUri = new AtomicReference<>();

        when(runtimeDelegate.createUriBuilder()).thenReturn(uriBuilder);
        when(uriBuilder.uri(any(URI.class))).thenAnswer(invocation -> {
            baseUri.set(invocation.getArgument(0));
            return uriBuilder;
        });
        when(uriBuilder.path(any(Class.class))).thenReturn(uriBuilder);
        when(uriBuilder.path(any(String.class))).thenReturn(uriBuilder);
        when(uriBuilder.build(any(Object[].class))).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            Object value = argument instanceof Object[] values ? values[0] : argument;
            String base = baseUri.get().toString();
            String separator = base.endsWith("/") ? "" : "/";
            return URI.create(base + separator + "realms/" + value);
        });

        RuntimeDelegate.setInstance(runtimeDelegate);
    }

    @Test
    void registersTheHigherPriorityEmailProviderFactory() {
        EmailTemplateProviderFactory factory = ServiceLoader.load(EmailTemplateProviderFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(UDSEmailTemplateProviderFactory.class::isInstance)
                .map(UDSEmailTemplateProviderFactory.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals("freemarker", factory.getId());
        assertEquals(1, factory.order());
    }

    @Test
    void rewritesAdminOriginExecuteActionsLinkToPublicOrigin() throws Exception {
        KeycloakSession session = sessionWithTokenEncoder(ADMIN_URL);
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn(REALM_NAME);

        UDSEmailTemplateProvider provider = new UDSEmailTemplateProvider(session, PUBLIC_URL);
        provider.setRealm(realm);
        String adminLink = actionLink(session, realm, ADMIN_URL);

        String publicLink = provider.publicExecuteActionsLink(adminLink);

        assertEquals(PUBLIC_URL.getHost(), URI.create(publicLink).getHost());
        ExecuteActionsActionToken publicToken = parseToken(publicLink);
        assertEquals(PUBLIC_URL + "realms/" + REALM_NAME, publicToken.getIssuer());
        assertEquals(PUBLIC_URL + "realms/" + REALM_NAME, publicToken.getAudience()[0]);
        assertEquals(USER_ID, publicToken.getUserId());
        assertEquals(EMAIL, publicToken.getEmail());
        assertEquals(List.of("UPDATE_PASSWORD"), publicToken.getRequiredActions());
        assertEquals(CLIENT_ID, publicToken.getIssuedFor());
        assertEquals("compound-auth-session-id", publicToken.getCompoundAuthenticationSessionId());
    }

    @Test
    void leavesLinksWithoutActionTokensUnchanged() throws EmailException {
        KeycloakSession session = mock(KeycloakSession.class);
        UDSEmailTemplateProvider provider = new UDSEmailTemplateProvider(session, PUBLIC_URL);

        String link = ADMIN_URL + "realms/" + REALM_NAME + "/login-actions/action-token";

        assertEquals(link, provider.publicExecuteActionsLink(link));
    }

    @Test
    void preservesThePublicOriginPathAndQuery() throws Exception {
        KeycloakSession session = sessionWithTokenEncoder(ADMIN_URL);
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn(REALM_NAME);
        UDSEmailTemplateProvider provider = new UDSEmailTemplateProvider(session, PUBLIC_URL);
        provider.setRealm(realm);

        String publicLink = provider.publicExecuteActionsLink(
                actionLink(session, realm, ADMIN_URL) + "&client_id=" + CLIENT_ID
        );

        URI uri = URI.create(publicLink);
        assertEquals(PUBLIC_URL.getHost(), uri.getHost());
        assertTrue(uri.getPath().endsWith("/realms/" + REALM_NAME + "/login-actions/action-token"));
        assertTrue(uri.getQuery().contains("client_id=" + CLIENT_ID));
    }

    private static String actionLink(KeycloakSession session, RealmModel realm, URI baseUri) {
        ExecuteActionsActionToken token = new ExecuteActionsActionToken(
                USER_ID,
                EMAIL,
                Math.toIntExact(Instant.now().plusSeconds(3600).getEpochSecond()),
                List.of("UPDATE_PASSWORD"),
                null,
                CLIENT_ID
        );
        token.id("token-id");
        token.setCompoundAuthenticationSessionId("compound-auth-session-id");
        return baseUri + "realms/" + REALM_NAME + "/login-actions/action-token?key="
                + token.serialize(session, realm, session.getContext().getUri());
    }

    private static ExecuteActionsActionToken parseToken(String link) throws Exception {
        String serializedToken = queryParameter(URI.create(link), "key");
        return org.keycloak.TokenVerifier
                .create(serializedToken, ExecuteActionsActionToken.class)
                .getToken();
    }

    private static String queryParameter(URI uri, String name) {
        for (String parameter : uri.getRawQuery().split("&")) {
            int separator = parameter.indexOf('=');
            if (name.equals(separator < 0 ? parameter : parameter.substring(0, separator))) {
                return separator < 0 ? "" : parameter.substring(separator + 1);
            }
        }
        throw new IllegalArgumentException("Missing query parameter: " + name);
    }

    private static KeycloakSession sessionWithTokenEncoder(URI requestBaseUri) {
        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext context = mock(KeycloakContext.class);
        KeycloakUriInfo uriInfo = mock(KeycloakUriInfo.class);
        TokenManager tokenManager = mock(TokenManager.class);
        when(session.getContext()).thenReturn(context);
        when(context.getUri()).thenReturn(uriInfo);
        when(uriInfo.getBaseUri()).thenReturn(requestBaseUri);
        when(session.tokens()).thenReturn(tokenManager);
        when(tokenManager.encode(any(Token.class))).thenAnswer(invocation -> {
            Token token = invocation.getArgument(0);
            String payload = JsonSerialization.writeValueAsString(token);
            String encodedHeader = encodeBase64Url("{\"alg\":\"HS256\"}");
            String encodedPayload = encodeBase64Url(payload);
            String encodedSignature = encodeBase64Url("signature");
            return encodedHeader + "." + encodedPayload + "." + encodedSignature;
        });
        return session;
    }

    private static String encodeBase64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
