/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.function.Executable;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.resources.admin.AdminAuth;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class ClientOwnershipFilterFixture {
    private final KeycloakSession session;
    private final RealmModel realm;
    private final ClientProvider clients;

    ClientOwnershipFilterFixture(KeycloakSession session, RealmModel realm, ClientProvider clients) {
        this.session = session;
        this.realm = realm;
        this.clients = clients;
    }

    static void assertForbidden(Executable action) {
        // The unit-test classpath has the Jakarta API, without a REST server implementation.
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(403);
        when(response.getStatusInfo()).thenReturn(Response.Status.FORBIDDEN);
        Response.ResponseBuilder builder = mock(Response.ResponseBuilder.class);
        when(builder.build()).thenReturn(response);
        try (var responses = mockStatic(Response.class)) {
            responses.when(() -> Response.status(Response.Status.FORBIDDEN)).thenReturn(builder);
            assertThrows(ForbiddenException.class, action);
        }
    }

    void authenticate(String clientId) {
        ClientModel application = mock(ClientModel.class);
        when(application.getClientId()).thenReturn(clientId);
        UserModel user = mock(UserModel.class);
        KeycloakContext context = mock(KeycloakContext.class);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        UserSessionModel userSession = mock(UserSessionModel.class);
        when(context.getUserSession()).thenReturn(userSession);
        when(userSession.getId()).thenReturn("session");
        when(userSession.getUser()).thenReturn(user);
        AccessToken token = new AccessToken();
        token.setSessionId("session");
        when(realm.getName()).thenReturn("uds");
        when(session.getAttribute(anyString(), eq(AdminAuth.class)))
                .thenReturn(new AdminAuth(realm, token, user, application));
    }

    ClientModel target(String owner) {
        when(session.clients()).thenReturn(clients);
        ClientModel target = mock(ClientModel.class);
        when(target.getAttribute("created-by")).thenReturn(owner);
        when(clients.getClientById(realm, "target")).thenReturn(target);
        return target;
    }

    static ContainerRequestContext request(String path) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uri = mock(UriInfo.class);
        when(request.getUriInfo()).thenReturn(uri);
        var segments = Stream.of(path.split("/")).map(value -> {
            PathSegment segment = mock(PathSegment.class);
            when(segment.getPath()).thenReturn(value);
            return segment;
        }).toList();
        when(uri.getPathSegments()).thenReturn(segments);
        return request;
    }

    static ClientRepresentation representation(String owner) {
        ClientRepresentation client = new ClientRepresentation();
        client.setSecret("test-secret");
        client.setRegistrationAccessToken("test-registration-token");
        client.setAttributes(new HashMap<>(Map.of(
                "client.secret.rotated", "test-rotated-secret",
                "jwt.credential.private.key", "test-private-key",
                "description", "unchanged")));
        if (owner != null) client.getAttributes().put("created-by", owner);
        return client;
    }
}
