/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClientIdAndKubernetesSecretAuthenticatorTest {

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
            ClientIdAndKubernetesSecretAuthenticator.readMountedClientSecret(secretPath.getParent().toString(), secretPath.getFileName().toString());
        } finally {
            Files.deleteIfExists(secretPath);
        }
    }
}