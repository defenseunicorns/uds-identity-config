/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.ClientAssertionState;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory.LookupResult;
import org.keycloak.cache.AlternativeLookupProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderStorageProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.JsonWebToken;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes.UDSKubernetesIdentityProviderFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UDSClientAssertionStrategyTest {

    private static final String SUBJECT = "system:serviceaccount:pepr-system:pepr-uds-core";
    private static final String K8S_ISSUER = "https://kubernetes.default.svc.cluster.local";
    private static final String AKS_ISSUER = "https://centralus.oic.prod-aks.azure.com/tenant-id";
    private static final String IDP_ALIAS = "uds";

    @Mock private ClientAuthenticationFlowContext context;
    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    @Mock private ClientAssertionState state;
    @Mock private JsonWebToken token;
    @Mock private AlternativeLookupProvider lookupProvider;
    @Mock private ClientModel clientModel;
    @Mock private IdentityProviderModel udsIdpModel;
    @Mock private IdentityProviderStorageProvider idpStorageProvider;

    private UDSClientAssertionStrategy strategy;

    @BeforeEach
    void setup() throws Exception {
        strategy = new UDSClientAssertionStrategy();

        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getState(eq(ClientAssertionState.class), any())).thenReturn(state);
        when(session.getProvider(AlternativeLookupProvider.class)).thenReturn(lookupProvider);
        when(session.identityProviders()).thenReturn(idpStorageProvider);

        when(state.getToken()).thenReturn(token);
        when(token.getSubject()).thenReturn(SUBJECT);
        when(token.getIssuer()).thenReturn(K8S_ISSUER);

        // UDS Kubernetes IdP setup
        when(udsIdpModel.getProviderId()).thenReturn(UDSKubernetesIdentityProviderFactory.PROVIDER_ID);
        when(udsIdpModel.isEnabled()).thenReturn(true);
        when(udsIdpModel.getAlias()).thenReturn(IDP_ALIAS);
    }

    @Test
    void testLookupDelegatesToDefaultWhenIssuerMatches() throws Exception {
        // Default strategy finds IdP by matching issuer
        IdentityProviderModel matchingIdp = mock(IdentityProviderModel.class);
        when(matchingIdp.isEnabled()).thenReturn(true);
        when(matchingIdp.getAlias()).thenReturn(IDP_ALIAS);

        when(lookupProvider.lookupIdentityProviderFromIssuer(session, K8S_ISSUER)).thenReturn(matchingIdp);
        when(lookupProvider.lookupClientFromClientAttributes(eq(session), anyMap())).thenReturn(clientModel);

        LookupResult result = strategy.lookup(context);

        assertNotNull(result);
        assertEquals(clientModel, result.clientModel());
        assertEquals(matchingIdp, result.identityProviderModel());
    }

    @Test
    void testLookupFallsBackToProviderTypeWhenIssuerMismatches() throws Exception {
        // AKS issuer -- default lookup returns null (no matching IdP)
        when(token.getIssuer()).thenReturn(AKS_ISSUER);
        when(lookupProvider.lookupIdentityProviderFromIssuer(session, AKS_ISSUER)).thenReturn(null);

        // Fallback: client has matching jwt.credential.sub, and jwt.credential.issuer points to uds-kubernetes IdP
        when(clientModel.getAttribute("jwt.credential.sub")).thenReturn(SUBJECT);
        when(clientModel.getAttribute("jwt.credential.issuer")).thenReturn(IDP_ALIAS);
        when(clientModel.getClientId()).thenReturn("uds-operator");
        when(realm.getClientsStream()).thenReturn(Stream.of(clientModel));
        when(idpStorageProvider.getByAlias(IDP_ALIAS)).thenReturn(udsIdpModel);

        LookupResult result = strategy.lookup(context);

        assertNotNull(result);
        assertEquals(clientModel, result.clientModel());
        assertEquals(udsIdpModel, result.identityProviderModel());
    }

    @Test
    void testLookupReturnsNullWhenNoMatchingClient() throws Exception {
        when(token.getIssuer()).thenReturn(AKS_ISSUER);
        when(lookupProvider.lookupIdentityProviderFromIssuer(session, AKS_ISSUER)).thenReturn(null);

        // No clients match the subject
        when(realm.getClientsStream()).thenReturn(Stream.empty());

        LookupResult result = strategy.lookup(context);

        assertNull(result);
    }

    @Test
    void testLookupReturnsNullWhenIdpNotFound() throws Exception {
        when(token.getIssuer()).thenReturn(AKS_ISSUER);
        when(lookupProvider.lookupIdentityProviderFromIssuer(session, AKS_ISSUER)).thenReturn(null);

        when(clientModel.getAttribute("jwt.credential.sub")).thenReturn(SUBJECT);
        when(clientModel.getAttribute("jwt.credential.issuer")).thenReturn(IDP_ALIAS);
        when(clientModel.getClientId()).thenReturn("uds-operator");
        when(realm.getClientsStream()).thenReturn(Stream.of(clientModel));
        // IdP not found by alias
        when(idpStorageProvider.getByAlias(IDP_ALIAS)).thenReturn(null);

        LookupResult result = strategy.lookup(context);

        assertNull(result);
    }

    @Test
    void testLookupSkipsDisabledIdps() throws Exception {
        when(token.getIssuer()).thenReturn(AKS_ISSUER);
        when(lookupProvider.lookupIdentityProviderFromIssuer(session, AKS_ISSUER)).thenReturn(null);

        when(clientModel.getAttribute("jwt.credential.sub")).thenReturn(SUBJECT);
        when(clientModel.getAttribute("jwt.credential.issuer")).thenReturn(IDP_ALIAS);
        when(clientModel.getClientId()).thenReturn("uds-operator");
        when(realm.getClientsStream()).thenReturn(Stream.of(clientModel));
        when(udsIdpModel.isEnabled()).thenReturn(false);
        when(idpStorageProvider.getByAlias(IDP_ALIAS)).thenReturn(udsIdpModel);

        LookupResult result = strategy.lookup(context);

        assertNull(result);
    }

    @Test
    void testLookupThrowsWhenMultipleClientsMatch() throws Exception {
        when(token.getIssuer()).thenReturn(AKS_ISSUER);
        when(lookupProvider.lookupIdentityProviderFromIssuer(session, AKS_ISSUER)).thenReturn(null);

        // Two clients with the same jwt.credential.sub
        ClientModel client1 = mock(ClientModel.class);
        when(client1.getAttribute("jwt.credential.sub")).thenReturn(SUBJECT);
        when(client1.getAttribute("jwt.credential.issuer")).thenReturn(IDP_ALIAS);
        when(client1.getClientId()).thenReturn("client-a");

        ClientModel client2 = mock(ClientModel.class);
        when(client2.getAttribute("jwt.credential.sub")).thenReturn(SUBJECT);
        when(client2.getAttribute("jwt.credential.issuer")).thenReturn(IDP_ALIAS);
        when(client2.getClientId()).thenReturn("client-b");

        when(realm.getClientsStream()).thenReturn(Stream.of(client1, client2));
        when(idpStorageProvider.getByAlias(IDP_ALIAS)).thenReturn(udsIdpModel);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> strategy.lookup(context));
        assertTrue(ex.getMessage().contains("matched 2 clients"));
    }

    @Test
    void testIsSupportedAssertionType() {
        assertTrue(strategy.isSupportedAssertionType(OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        assertFalse(strategy.isSupportedAssertionType("some-other-type"));
        assertFalse(strategy.isSupportedAssertionType(null));
    }
}
