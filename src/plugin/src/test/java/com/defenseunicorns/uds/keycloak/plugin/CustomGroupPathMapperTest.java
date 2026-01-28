/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.*;
import org.keycloak.representations.IDToken;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
public class CustomGroupPathMapperTest {

    @Mock
    KeycloakSession keycloakSession;
    @Mock
    UserSessionModel userSession;
    @Mock
    ClientSessionContext clientSessionCtx;
    @Mock
    ProtocolMapperModel mappingModel;
    @Mock
    UserModel user;

    CustomGroupPathMapper mapper;
    IDToken token;

    @BeforeEach
    public void setUp() {
        mapper = new CustomGroupPathMapper();
        keycloakSession = mock(KeycloakSession.class);
        userSession = mock(UserSessionModel.class);
        clientSessionCtx = mock(ClientSessionContext.class);
        mappingModel = mock(ProtocolMapperModel.class);
        user = mock(UserModel.class);
        token = new IDToken();

        when(userSession.getUser()).thenReturn(user);
    }

    @Test
    public void testSetClaim_withLeadingSlashes() {
        List<String> groups = List.of("/group1", "/group2/subgroup");
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        List<String> expected = List.of("group1", "group2/subgroup");
        assertEquals(expected, token.getOtherClaims().get("bare-groups"));
    }

    @Test
    public void testSetClaim_withoutLeadingSlashes() {
        List<String> groups = List.of("group1", "group2/subgroup");
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        List<String> expected = List.of("group1", "group2/subgroup");
        assertEquals(expected, token.getOtherClaims().get("bare-groups"));
    }

    @Test
    public void testSetClaim_emptyGroups() {
        List<String> groups = List.of();
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        assertNull(token.getOtherClaims().get("bare-groups"));
    }

    @Test
    public void testSetClaim_noGroupsClaim() {
        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        assertNull(token.getOtherClaims().get("bare-groups"));
    }
}
