/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.keycloak.broker.kubernetes.KubernetesConstants.SERVICE_ACCOUNT_TOKEN_PATH;

public class KubernetesUtilsTest {

    @Test
    void testReadServiceAccountTokenReturnsTokenContent(@TempDir Path tempDir) throws Exception {
        Path tokenFile = tempDir.resolve("token");
        Files.write(tokenFile, "my-sa-token".getBytes(StandardCharsets.UTF_8));

        try (MockedStatic<java.nio.file.Paths> pathsMock = Mockito.mockStatic(java.nio.file.Paths.class)) {
            pathsMock.when(() -> java.nio.file.Paths.get(SERVICE_ACCOUNT_TOKEN_PATH))
                .thenReturn(tokenFile);

            String result = KubernetesUtils.readServiceAccountToken();
            assertEquals("my-sa-token", result);
        }
    }

    @Test
    void testReadServiceAccountTokenTrimsWhitespace(@TempDir Path tempDir) throws Exception {
        Path tokenFile = tempDir.resolve("token");
        Files.write(tokenFile, "my-sa-token\n  ".getBytes(StandardCharsets.UTF_8));

        try (MockedStatic<java.nio.file.Paths> pathsMock = Mockito.mockStatic(java.nio.file.Paths.class)) {
            pathsMock.when(() -> java.nio.file.Paths.get(SERVICE_ACCOUNT_TOKEN_PATH))
                .thenReturn(tokenFile);

            String result = KubernetesUtils.readServiceAccountToken();
            assertEquals("my-sa-token", result);
        }
    }

    @Test
    void testReadServiceAccountTokenReturnsNullWhenFileDoesNotExist(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist");

        try (MockedStatic<java.nio.file.Paths> pathsMock = Mockito.mockStatic(java.nio.file.Paths.class)) {
            pathsMock.when(() -> java.nio.file.Paths.get(SERVICE_ACCOUNT_TOKEN_PATH))
                .thenReturn(nonExistent);

            String result = KubernetesUtils.readServiceAccountToken();
            assertNull(result);
        }
    }

    @Test
    void testExecuteAndParseReturnsDeserializedBody() throws Exception {
        SimpleHttpRequest request = mock(SimpleHttpRequest.class);
        SimpleHttpResponse response = mock(SimpleHttpResponse.class);
        when(request.asResponse()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.asString()).thenReturn("{\"value\":\"hello\"}");

        TestPayload result = KubernetesUtils.executeAndParse(request, "https://example.com", TestPayload.class);
        assertEquals("hello", result.value);
    }

    @Test
    void testExecuteAndParseThrowsOnNon2xx() throws Exception {
        SimpleHttpRequest request = mock(SimpleHttpRequest.class);
        SimpleHttpResponse response = mock(SimpleHttpResponse.class);
        when(request.asResponse()).thenReturn(response);
        when(response.getStatus()).thenReturn(503);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> KubernetesUtils.executeAndParse(request, "https://example.com/api", TestPayload.class));
        assertTrue(ex.getMessage().contains("https://example.com/api"));
        assertTrue(ex.getMessage().contains("503"));
    }

    /** Simple POJO for JSON deserialization tests. */
    public static class TestPayload {
        public String value;
    }

    @Test
    void testReadServiceAccountTokenReturnsNullOnException() {
        try (MockedStatic<java.nio.file.Paths> pathsMock = Mockito.mockStatic(java.nio.file.Paths.class)) {
            pathsMock.when(() -> java.nio.file.Paths.get(SERVICE_ACCOUNT_TOKEN_PATH))
                .thenThrow(new RuntimeException("simulated failure"));

            String result = KubernetesUtils.readServiceAccountToken();
            assertNull(result);
        }
    }
}
