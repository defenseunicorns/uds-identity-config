/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests the fail-loud behavior of {@link UDSKubernetesJwksEndpointLoader#loadKeys}: a discovery or JWKS fetch that
 * yields no usable keys must throw rather than return an empty key set (which would make signature verification
 * silently fail or behave unpredictably). The underlying HTTP/token-forwarding is covered by
 * {@link KubernetesUtilsTest}, so it is stubbed here.
 */
class UDSKubernetesJwksEndpointLoaderTest {

    private static final String ISSUER = "https://issuer.example";
    private static final String JWKS_URI = "https://issuer.example/openid/v1/jwks";

    private final KeycloakSession session = mock(KeycloakSession.class);
    private final UDSKubernetesJwksEndpointLoader loader = new UDSKubernetesJwksEndpointLoader(session, ISSUER);

    private void stubDiscovery(MockedStatic<KubernetesUtils> utils, OIDCConfigurationRepresentation discovery) {
        utils.when(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/json"),
                eq(OIDCConfigurationRepresentation.class), any())).thenReturn(discovery);
    }

    private void stubJwks(MockedStatic<KubernetesUtils> utils, JSONWebKeySet jwks) {
        utils.when(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/jwk-set+json"),
                eq(JSONWebKeySet.class), any())).thenReturn(jwks);
    }

    @Test
    void throwsWhenDiscoveryDocumentIsNull() {
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, null);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void throwsWhenJwksUriMissing() {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn(null);
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void throwsWhenJwksUriBlank() {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn("   ");
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void throwsWhenJwksDocumentHasNoKeys() {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn(JWKS_URI);
        JSONWebKeySet jwks = new JSONWebKeySet(); // default getKeys() == null
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            stubJwks(utils, jwks);
            assertThrows(IllegalStateException.class, loader::loadKeys);
        }
    }

    @Test
    void returnsWrapperWhenKeysPresent() throws Exception {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn(JWKS_URI);
        JSONWebKeySet jwks = new JSONWebKeySet();
        jwks.setKeys(new JWK[0]); // non-null key array clears the guard
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubDiscovery(utils, discovery);
            stubJwks(utils, jwks);
            PublicKeysWrapper wrapper = loader.loadKeys();
            assertNotNull(wrapper);
        }
    }

    // ---- token routing: the pod token is forwarded only to the in-cluster API server, and only if its own
    //      issuer matches the issuer we're loading keys for. The SA token is mounted as a real temp file (via the
    //      override property) so the actual read + JWS parse in getToken is exercised, not stubbed. ----

    private static final String IN_CLUSTER = "https://kubernetes.default.svc.cluster.local";

    @TempDir
    Path tokenDir;

    @AfterEach
    void clearTokenPathOverride() {
        System.clearProperty(KubernetesUtils.SERVICE_ACCOUNT_TOKEN_PATH_PROPERTY);
    }

    /** Write a minimal unsigned-shape JWS (header.payload.sig) with an {@code iss} claim to the mounted token path. */
    private void mountToken(String iss) throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"iss\":\"" + iss + "\"}").getBytes(StandardCharsets.UTF_8));
        String sig = enc.encodeToString("sig".getBytes(StandardCharsets.UTF_8));
        Path tokenFile = tokenDir.resolve("token");
        Files.writeString(tokenFile, header + "." + payload + "." + sig);
        System.setProperty(KubernetesUtils.SERVICE_ACCOUNT_TOKEN_PATH_PROPERTY, tokenFile.toString());
    }

    private void stubReadyDiscovery(MockedStatic<KubernetesUtils> utils, String jwksUri) {
        OIDCConfigurationRepresentation discovery = mock(OIDCConfigurationRepresentation.class);
        when(discovery.getJwksUri()).thenReturn(jwksUri);
        JSONWebKeySet jwks = new JSONWebKeySet();
        jwks.setKeys(new JWK[0]);
        stubDiscovery(utils, discovery);
        stubJwks(utils, jwks);
    }

    @Test
    void forwardsTokenToTrustedInClusterIssuerWhenIssMatches() throws Exception {
        mountToken(IN_CLUSTER);
        UDSKubernetesJwksEndpointLoader inCluster = new UDSKubernetesJwksEndpointLoader(session, IN_CLUSTER);
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubReadyDiscovery(utils, IN_CLUSTER + "/openid/v1/jwks");

            inCluster.loadKeys();

            // trusted in-cluster destination + matching iss → token forwarded (non-null)
            utils.verify(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/json"),
                    eq(OIDCConfigurationRepresentation.class), anyString()));
        }
    }

    @Test
    void dropsTokenWhenPodTokenIssuerMismatches() throws Exception {
        mountToken("https://some-other-issuer.example");
        UDSKubernetesJwksEndpointLoader inCluster = new UDSKubernetesJwksEndpointLoader(session, IN_CLUSTER);
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubReadyDiscovery(utils, IN_CLUSTER + "/openid/v1/jwks");

            inCluster.loadKeys();

            // pod token's iss != loader issuer → never forwarded
            utils.verify(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/json"),
                    eq(OIDCConfigurationRepresentation.class), isNull()));
        }
    }

    @Test
    void neverForwardsTokenToExternalIssuer() throws Exception {
        String externalIssuer = "https://oidc.eks.us-gov-west-1.amazonaws.com/id/ABC";
        mountToken(externalIssuer); // even though the pod token's iss matches the external issuer
        UDSKubernetesJwksEndpointLoader external = new UDSKubernetesJwksEndpointLoader(session, externalIssuer);
        try (MockedStatic<KubernetesUtils> utils = mockStatic(KubernetesUtils.class, CALLS_REAL_METHODS)) {
            stubReadyDiscovery(utils, "https://s3.amazonaws.com/eks/keys.json");

            external.loadKeys();

            // external (untrusted) issuer → token withheld from both discovery and JWKS fetches
            utils.verify(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/json"),
                    eq(OIDCConfigurationRepresentation.class), isNull()));
            utils.verify(() -> KubernetesUtils.fetchJson(any(), anyString(), eq("application/jwk-set+json"),
                    eq(JSONWebKeySet.class), isNull()));
        }
    }
}
