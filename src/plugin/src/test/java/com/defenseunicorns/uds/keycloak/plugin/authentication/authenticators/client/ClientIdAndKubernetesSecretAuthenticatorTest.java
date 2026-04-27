/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.ClientAuthUtil;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ClientIdAndKubernetesSecretAuthenticatorTest {

    private static final String CLIENT_ID = "test-client";

    @Mock
    private ClientAuthenticationFlowContext context;
    @Mock
    private KeycloakSession session;
    @Mock
    private HttpRequest httpRequest;
    @Mock
    private HttpHeaders httpHeaders;
    @Mock
    private RealmModel realm;
    @Mock
    private ClientProvider clientProvider;
    @Mock
    private ClientModel client;
    @Mock
    private EventBuilder eventBuilder;

    @TempDir
    Path secretDir;

    private ClientIdAndKubernetesSecretAuthenticator authenticator;
    private MockedStatic<ClientAuthUtil> clientAuthUtilMock;

    @AfterEach
    void tearDown() {
        if (clientAuthUtilMock != null) {
            clientAuthUtilMock.close();
        }
    }

    @BeforeEach
    void setup() throws Exception {
        // ClientAuthUtil.errorResponse calls Response.status(...).build(), which needs a
        // JAX-RS RuntimeDelegate not on the test classpath. Stub it to return a mock
        // Response whose getStatus() echoes the status code argument.
        clientAuthUtilMock = mockStatic(ClientAuthUtil.class);
        clientAuthUtilMock.when(() -> ClientAuthUtil.errorResponse(anyInt(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    Response r = org.mockito.Mockito.mock(Response.class);
                    when(r.getStatus()).thenReturn((int) inv.getArgument(0));
                    return r;
                });

        authenticator = new ClientIdAndKubernetesSecretAuthenticator();
        Field f = ClientIdAndKubernetesSecretAuthenticator.class.getDeclaredField("secretMountPath");
        f.setAccessible(true);
        f.set(authenticator, secretDir.toString());

        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(httpRequest.getHttpHeaders()).thenReturn(httpHeaders);
        when(httpHeaders.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(httpHeaders.getMediaType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getEvent()).thenReturn(eventBuilder);
        when(eventBuilder.client(any(String.class))).thenReturn(eventBuilder);
        when(session.clients()).thenReturn(clientProvider);
        when(clientProvider.getClientByClientId(realm, CLIENT_ID)).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.isPublicClient()).thenReturn(false);
    }

    private void writeMountedSecret(String value) throws Exception {
        Files.writeString(secretDir.resolve(CLIENT_ID), value);
    }

    private void setFormData(String clientId, String clientSecret) {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        if (clientId != null) {
            form.add(OAuth2Constants.CLIENT_ID, clientId);
        }
        if (clientSecret != null) {
            form.add(OAuth2Constants.CLIENT_SECRET, clientSecret);
        }
        when(httpRequest.getDecodedFormParameters()).thenReturn(form);
    }

    @Test
    void shouldAuthenticateWhenSecretsMatch() throws Exception {
        writeMountedSecret("supersecret");
        setFormData(CLIENT_ID, "supersecret");

        authenticator.authenticateClient(context);

        verify(context).setClient(client);
        verify(context).success();
        verify(context, never()).failure(any(), any());
    }

    @Test
    void shouldFailWhenProvidedSecretDiffersFromMounted() throws Exception {
        // Regression: previously `clientSecret = mountedClientSecret.trim()` clobbered
        // the caller-supplied value, causing all requests to succeed.
        writeMountedSecret("supersecret");
        setFormData(CLIENT_ID, "wrong-secret");

        authenticator.authenticateClient(context);

        verify(context).failure(eq(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS), any(Response.class));
        verify(context, never()).success();
    }

    @Test
    void shouldTrimWhitespaceOnBothSides() throws Exception {
        writeMountedSecret("  supersecret\n");
        setFormData(CLIENT_ID, " supersecret ");

        authenticator.authenticateClient(context);

        verify(context).success();
    }

    @Test
    void shouldChallengeWhenClientSecretMissing() throws Exception {
        writeMountedSecret("supersecret");
        setFormData(CLIENT_ID, null);

        authenticator.authenticateClient(context);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(context).challenge(captor.capture());
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), captor.getValue().getStatus());
        verify(context, never()).success();
    }

    @Test
    void shouldChallengeWhenClientIdMissing() {
        setFormData(null, "supersecret");

        authenticator.authenticateClient(context);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(context).challenge(captor.capture());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), captor.getValue().getStatus());
        verify(context, never()).success();
    }

    @Test
    void shouldFailWhenClientNotFound() {
        when(clientProvider.getClientByClientId(realm, CLIENT_ID)).thenReturn(null);
        setFormData(CLIENT_ID, "supersecret");

        authenticator.authenticateClient(context);

        verify(context).failure(eq(AuthenticationFlowError.CLIENT_NOT_FOUND), any());
        verify(context, never()).success();
    }

    @Test
    void shouldFailWhenClientDisabled() {
        when(client.isEnabled()).thenReturn(false);
        setFormData(CLIENT_ID, "supersecret");

        authenticator.authenticateClient(context);

        verify(context).failure(eq(AuthenticationFlowError.CLIENT_DISABLED), any());
        verify(context, never()).success();
    }

    @Test
    void shouldSucceedForPublicClientWithoutSecretCheck() {
        when(client.isPublicClient()).thenReturn(true);
        setFormData(CLIENT_ID, null);

        authenticator.authenticateClient(context);

        verify(context).success();
        verify(context, never()).failure(any(), any());
    }

    @Test
    void shouldReturnInternalErrorWhenMountFileMissing() {
        // No secret file written.
        setFormData(CLIENT_ID, "supersecret");

        authenticator.authenticateClient(context);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(context).failure(eq(AuthenticationFlowError.INTERNAL_ERROR), captor.capture());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), captor.getValue().getStatus());
        verify(context, never()).success();
    }

    @Test
    void shouldReturnInternalErrorWhenMountFileEmpty() throws Exception {
        writeMountedSecret("");
        setFormData(CLIENT_ID, "supersecret");

        authenticator.authenticateClient(context);

        verify(context).failure(eq(AuthenticationFlowError.INTERNAL_ERROR), any());
        verify(context, never()).success();
    }

    @Test
    public void shouldThrowErrorOnMissingClientsSecretsFiles() {
        String secretMountPath = "/tmp/secrets";
        String clientId = "missing-client";

        assertThrows(NoSuchFileException.class, () -> {
            ClientIdAndKubernetesSecretAuthenticator.readMountedClientSecret(secretMountPath, clientId);
        });
    }

    @Test
    public void shouldReadProperlyConstructedFile() throws Exception {
        String secretMountPath = System.getProperty("java.io.tmpdir");
        String clientId = "empty-client";

        Path secretPath = Paths.get(secretMountPath, clientId);
        Files.createDirectories(secretPath.getParent());
        Files.writeString(secretPath, "test");

        try {
            ClientIdAndKubernetesSecretAuthenticator.readMountedClientSecret(
                    secretPath.getParent().toString(), secretPath.getFileName().toString());
        } finally {
            Files.deleteIfExists(secretPath);
        }
    }
}
