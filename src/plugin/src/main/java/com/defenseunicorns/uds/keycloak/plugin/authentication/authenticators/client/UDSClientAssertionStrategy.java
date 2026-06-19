/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes.UDSKubernetesIdentityProviderFactory;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.ClientAssertionState;
import org.keycloak.authentication.authenticators.client.DefaultClientAssertionStrategy;
import org.keycloak.authentication.authenticators.client.FederatedJWTClientAuthenticator;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory;
import org.keycloak.models.ClientModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the client behind a federated JWT client assertion.
 * <p>
 * Tries the stock {@link DefaultClientAssertionStrategy} first (which resolves the IdP by matching the token
 * issuer). When that fails — common on managed/external issuers where the configured issuer differs from the
 * token's — it falls back to resolving via client attributes: a client is accepted only if its
 * {@code jwt.credential.sub} matches the token subject AND its {@code jwt.credential.issuer} references an
 * enabled {@code uds-kubernetes} IdP alias. A bare subject match is never sufficient.
 *
 * <p>The delegate path mirrors Keycloak 26.6.3 DefaultClientAssertionStrategy.lookup:
 * https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/authenticators/client/DefaultClientAssertionStrategy.java#L21
 *
 * <p><b>WORKAROUND (keycloak#49039):</b> the attribute-based fallback exists only because the stock provider
 * can't resolve clients for managed/external issuers safely. Delete this strategy once
 * <a href="https://github.com/keycloak/keycloak/issues/49039">keycloak/keycloak#49039</a> is resolved.
 */
public class UDSClientAssertionStrategy implements ClientAssertionIdentityProviderFactory.ClientAssertionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ClientAssertionIdentityProviderFactory.ClientAssertionStrategy delegate = new DefaultClientAssertionStrategy();

    @Override
    public boolean isSupportedAssertionType(String assertionType) {
        return delegate.isSupportedAssertionType(assertionType);
    }

    @Override
    public ClientAssertionIdentityProviderFactory.LookupResult lookup(ClientAuthenticationFlowContext context) throws Exception {
        ClientAssertionIdentityProviderFactory.LookupResult result = delegate.lookup(context);
        if (result != null && result.clientModel() != null) {
            return result; // stock issuer-based resolution succeeded
        }
        return fallbackLookup(context);
    }

    private ClientAssertionIdentityProviderFactory.LookupResult fallbackLookup(ClientAuthenticationFlowContext context) throws Exception {
        ClientAssertionState state = context.getState(ClientAssertionState.class, ClientAssertionState.supplier());
        JsonWebToken token = state.getToken();
        if (token == null || token.getSubject() == null || token.getSubject().isBlank()) {
            return null;
        }
        String subject = token.getSubject();
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        String clientIdParam = context.getHttpRequest().getDecodedFormParameters().getFirst(OAuth2Constants.CLIENT_ID);
        if (clientIdParam != null && !clientIdParam.isBlank()) {
            return validateCandidate(session, realm.getClientByClientId(clientIdParam), subject);
        }

        // No client_id: resolve by subject, requiring exactly one uds-kubernetes-backed client (fail closed otherwise).
        List<ClientAssertionIdentityProviderFactory.LookupResult> matches = session.clients()
                .searchClientsByAttributes(realm, Map.of(FederatedJWTClientAuthenticator.JWT_CREDENTIAL_SUBJECT_KEY, subject), null, null)
                .map(client -> validateCandidate(session, client, subject))
                .filter(Objects::nonNull)
                .limit(2)
                .toList();

        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            logger.warn("Ambiguous federated client assertion: multiple uds-kubernetes clients match sub, rejecting");
        }
        return null;
    }

    /**
     * Accept a candidate client only when it matches the token subject and references an enabled uds-kubernetes
     * IdP via {@code jwt.credential.issuer}. Returns null (fail closed) otherwise.
     */
    ClientAssertionIdentityProviderFactory.LookupResult validateCandidate(KeycloakSession session, ClientModel client, String subject) {
        if (client == null) {
            return null;
        }
        if (!subject.equals(client.getAttribute(FederatedJWTClientAuthenticator.JWT_CREDENTIAL_SUBJECT_KEY))) {
            return null;
        }
        String alias = client.getAttribute(FederatedJWTClientAuthenticator.JWT_CREDENTIAL_ISSUER_KEY);
        if (alias == null || alias.isBlank()) {
            return null;
        }
        IdentityProviderModel idp = session.identityProviders().getByAlias(alias);
        if (idp == null || !idp.isEnabled()) {
            return null;
        }
        if (!UDSKubernetesIdentityProviderFactory.PROVIDER_ID.equals(idp.getProviderId())) {
            return null;
        }
        return new ClientAssertionIdentityProviderFactory.LookupResult(client, idp);
    }
}
