/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.models.*;
import org.keycloak.representations.IDToken;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ IDToken.class, KeycloakSession.class, UserSessionModel.class, ClientSessionContext.class })
@PowerMockIgnore("javax.management.*")
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

    @Before
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
