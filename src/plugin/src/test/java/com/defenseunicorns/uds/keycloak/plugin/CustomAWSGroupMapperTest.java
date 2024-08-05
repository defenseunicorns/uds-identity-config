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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ IDToken.class, KeycloakSession.class, UserSessionModel.class, ClientSessionContext.class })
@PowerMockIgnore("javax.management.*")
public class CustomAWSGroupMapperTest {

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

    CustomAWSGroupMapper mapper;
    IDToken token;

    @Before
    public void setup() {
        mapper = new CustomAWSGroupMapper();
        keycloakSession = mock(KeycloakSession.class);
        userSession = mock(UserSessionModel.class);
        clientSessionCtx = mock(ClientSessionContext.class);
        mappingModel = mock(ProtocolMapperModel.class);
        user = mock(UserModel.class);
        token = new IDToken();

        when(userSession.getUser()).thenReturn(user);
    }

    @Test
    public void testSetClaim_withValidGroups() {
        List<String> groups = List.of("group1", "group2", "group3");
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        String expected = "group1:group2:group3";
        assertEquals(expected, token.getOtherClaims().get("aws-groups"));
    }

    @Test
    public void testSetClaim_noGroupsClaim() {
        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        // aws-groups should be an empty string when there is no groups claim
        assertEquals("", token.getOtherClaims().get("aws-groups"));
    }

    @Test
    public void testSetClaim_emptyGroups() {
        List<String> groups = List.of();
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        // aws-groups should be an empty string when groups list is empty
        assertEquals("", token.getOtherClaims().get("aws-groups"));
    }

    @Test
    public void testSetClaim_withNonStringElements() {
        List<Object> groups = List.of(1, 2, 3);
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        // aws-groups should be an empty string when groups list contains non-string elements
        assertEquals("", token.getOtherClaims().get("aws-groups"));
    }

    @Test
    public void testSetClaim_withMixedElements() {
        List<Object> groups = List.of("group1", 2, "group3");
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        String expected = "group1:group3";
        assertEquals(expected, token.getOtherClaims().get("aws-groups"));
    }

    @Test
    public void testSetClaim_withSlashInGroupPaths() {
        List<String> groups = List.of("/group1/group2", "/group1/group3");
        token.getOtherClaims().put("groups", groups);

        mapper.setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);

        // We expect the slashes to be preserved
        String expected = "/group1/group2:/group1/group3";
        assertEquals(expected, token.getOtherClaims().get("aws-groups"));
    }
}