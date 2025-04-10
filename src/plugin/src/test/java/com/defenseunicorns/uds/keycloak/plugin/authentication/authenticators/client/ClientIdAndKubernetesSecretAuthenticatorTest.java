/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runner.RunWith;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(BlockJUnit4ClassRunner.class)
public class ClientIdAndKubernetesSecretAuthenticatorTest {

    @Test(expected = NoSuchFileException.class)
    public void shouldThrowErrorOnMissingClientsSecretsFiles() throws Exception {
        String secretMountPath = "/tmp/secrets";
        String clientId = "missing-client";

        ClientIdAndKubernetesSecretAuthenticator.readMountedClientSecret(secretMountPath, clientId);
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