/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakUriInfo;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.urls.UrlType;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class UDSAdminApiAudienceMapperTest {
    @Test
    void addsRequestFrontendUriAsAudienceToAccessAndIntrospectionTokens() {
        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext context = mock(KeycloakContext.class);
        KeycloakUriInfo requestUri = mock(KeycloakUriInfo.class);
        ClientModel client = mock(ClientModel.class);
        RealmModel realm = mock(RealmModel.class);
        when(session.getContext()).thenReturn(context);
        when(context.getClient()).thenReturn(client);
        when(context.getUri(UrlType.FRONTEND)).thenReturn(requestUri);
        when(realm.getName()).thenReturn("uds");

        List<String[]> requestUris = List.of(
                new String[]{"https://keycloak.admin.uds.dev/", "https://keycloak.admin.uds.dev/admin/realms/uds"},
                new String[]{"http://keycloak.uds-kc.svc.cluster.local:8080/", "http://keycloak.uds-kc.svc.cluster.local:8080/admin/realms/uds"},
                new String[]{"https://sso-alt.example.test/", "https://sso-alt.example.test/admin/realms/uds"});
        for (String[] request : requestUris) {
            UriBuilder baseBuilder = mock(UriBuilder.class);
            UriBuilder adminBuilder = mock(UriBuilder.class);
            when(requestUri.getBaseUriBuilder()).thenReturn(baseBuilder);
            when(context.getRealm()).thenReturn(realm);
            when(adminBuilder.path("uds")).thenReturn(adminBuilder);
            when(adminBuilder.build()).thenReturn(URI.create(request[1]));
            try (var adminRoot = mockStatic(AdminRoot.class)) {
                adminRoot.when(() -> AdminRoot.realmsUrl(baseBuilder)).thenReturn(adminBuilder);
                UDSAdminApiAudienceMapper mapper = new UDSAdminApiAudienceMapper();
                ProtocolMapperModel config = UDSAdminApiAudienceMapper.createMapper("test");
                AccessToken accessToken = mapper.transformAccessToken(new AccessToken(), config, session, null, null);
                AccessToken introspectionToken = mapper.transformIntrospectionToken(new AccessToken(), config, session, null, null);
                assertEquals(List.of(request[1]), List.of(accessToken.getAudience()));
                assertEquals(List.of(request[1]), List.of(introspectionToken.getAudience()));
            }
        }
    }
}
