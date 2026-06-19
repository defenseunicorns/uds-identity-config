/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.ClientModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderStorageProvider;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit-tests the candidate-acceptance rules of the fallback lookup. The full lookup() branching (client_id
 * present/absent, ambiguity, delegation to the default strategy) is exercised by the uds-core integration test,
 * which runs the real client-authentication flow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UDSClientAssertionStrategyTest {

    private static final String SUB = "system:serviceaccount:uds-fleet-command:uds-fleet-command-sa";
    private static final String ALIAS = "kubernetes";
    private static final String SUBJECT_KEY = "jwt.credential.sub";
    private static final String ISSUER_KEY = "jwt.credential.issuer";

    private final UDSClientAssertionStrategy strategy = new UDSClientAssertionStrategy();

    @Mock KeycloakSession session;
    @Mock IdentityProviderStorageProvider idpStorage;
    @Mock ClientModel client;
    @Mock IdentityProviderModel idp;

    @BeforeEach
    void setUp() {
        when(session.identityProviders()).thenReturn(idpStorage);
        when(idpStorage.getByAlias(ALIAS)).thenReturn(idp);
        when(client.getAttribute(SUBJECT_KEY)).thenReturn(SUB);
        when(client.getAttribute(ISSUER_KEY)).thenReturn(ALIAS);
        when(idp.isEnabled()).thenReturn(true);
        when(idp.getProviderId()).thenReturn("uds-kubernetes");
    }

    @Test
    void acceptsClientBackedByEnabledUdsKubernetesIdp() {
        assertNotNull(strategy.validateCandidate(session, client, SUB));
    }

    @Test
    void rejectsNullClient() {
        assertNull(strategy.validateCandidate(session, null, SUB));
    }

    @Test
    void rejectsWhenSubjectDoesNotMatch() {
        when(client.getAttribute(SUBJECT_KEY)).thenReturn("system:serviceaccount:other:sa");
        assertNull(strategy.validateCandidate(session, client, SUB));
    }

    @Test
    void rejectsWhenIssuerAliasMissing() {
        when(client.getAttribute(ISSUER_KEY)).thenReturn(null);
        assertNull(strategy.validateCandidate(session, client, SUB));
    }

    @Test
    void rejectsWhenIdpDisabled() {
        when(idp.isEnabled()).thenReturn(false);
        assertNull(strategy.validateCandidate(session, client, SUB));
    }

    @Test
    void rejectsWhenIdpIsNotUdsKubernetes() {
        when(idp.getProviderId()).thenReturn("kubernetes");
        assertNull(strategy.validateCandidate(session, client, SUB));
    }

    @Test
    void rejectsWhenIdpAliasUnresolved() {
        when(idpStorage.getByAlias(ALIAS)).thenReturn(null);
        assertNull(strategy.validateCandidate(session, client, SUB));
    }
}
