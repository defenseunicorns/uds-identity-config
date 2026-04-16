/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication.authenticators.client;

import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.authentication.authenticators.client.ClientAssertionState;
import org.keycloak.authentication.authenticators.client.DefaultClientAssertionStrategy;
import org.keycloak.authentication.authenticators.client.FederatedJWTClientAuthenticator;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory.ClientAssertionStrategy;
import org.keycloak.broker.provider.ClientAssertionIdentityProviderFactory.LookupResult;
import org.keycloak.models.ClientModel;
import org.keycloak.models.IdentityProviderModel;

import com.defenseunicorns.uds.keycloak.plugin.broker.kubernetes.UDSKubernetesIdentityProviderFactory;

import java.util.Objects;

import org.jboss.logging.Logger;

/**
 * Issuer-agnostic client assertion strategy for managed Kubernetes clusters.
 *
 * <h2>Why this exists</h2>
 *
 * The upstream {@link DefaultClientAssertionStrategy} looks up identity providers by matching
 * the JWT assertion's {@code iss} claim against configured IdP issuer URLs. On self-hosted
 * clusters (e.g. k3d), the SA token issuer is {@code https://kubernetes.default.svc.cluster.local}
 * which matches the configured IdP — so the default strategy works.
 *
 * On managed Kubernetes clusters (EKS, AKS, GKE), the SA token issuer is a cloud-specific URL
 * (e.g. {@code https://oidc.eks.us-gov-west-1.amazonaws.com/id/...}) that does NOT match the
 * configured IdP issuer. The default strategy returns null, and authentication fails with
 * {@code client_not_found} before our custom IdP's {@code verifyClientAssertion} is ever called.
 *
 * <h2>How the fallback works</h2>
 *
 * When the default issuer-based lookup fails, this strategy uses a client-first approach:
 * <ol>
 *   <li>Scan realm clients for one whose {@code jwt.credential.sub} matches the JWT subject</li>
 *   <li>Read that client's {@code jwt.credential.issuer} attribute (which is the IdP alias)</li>
 *   <li>Look up the IdP by alias via {@code IdentityProviderStorageProvider.getByAlias()}</li>
 *   <li>Verify the IdP uses our {@code uds-kubernetes} provider type and is enabled</li>
 * </ol>
 *
 * <h2>Why we don't use {@code getIdentityProvidersStream()}</h2>
 *
 * In Keycloak 26.5, {@code RealmModel.getIdentityProvidersStream()} delegates to
 * {@code IdentityProviderStorageProvider.getAllStream(IdentityProviderQuery.userAuthentication())},
 * which only returns {@code USER_AUTHENTICATION} type IdPs. Our {@code uds-kubernetes} IdP has
 * type {@code CLIENT_ASSERTION}, so it is excluded from that stream. Using
 * {@code getByAlias()} bypasses this filter entirely.
 *
 * @see DefaultClientAssertionStrategy
 * @see UDSKubernetesIdentityProvider
 */
public class UDSClientAssertionStrategy implements ClientAssertionStrategy {

    private static final Logger LOGGER = Logger.getLogger(UDSClientAssertionStrategy.class);

    private final DefaultClientAssertionStrategy defaultStrategy = new DefaultClientAssertionStrategy();

    @Override
    public boolean isSupportedAssertionType(String assertionType) {
        return OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT.equals(assertionType);
    }

    @Override
    public LookupResult lookup(ClientAuthenticationFlowContext context) throws Exception {
        // 1. Try standard issuer-based lookup (handles k3d and matching-issuer cases)
        LookupResult defaultResult = defaultStrategy.lookup(context);
        if (defaultResult != null
                && defaultResult.identityProviderModel() != null
                && defaultResult.identityProviderModel().isEnabled()
                && defaultResult.clientModel() != null) {
            LOGGER.debug("Standard issuer-based lookup succeeded");
            return defaultResult;
        }

        // 2. Fallback: client-first lookup for managed K8s clusters where issuer doesn't match
        LOGGER.debug("Standard issuer-based lookup failed, falling back to client-first lookup");
        ClientAssertionState state = context.getState(ClientAssertionState.class, ClientAssertionState.supplier());
        String subject = state != null && state.getToken() != null ? state.getToken().getSubject() : null;
        if (subject == null) {
            LOGGER.debug("Fallback lookup skipped because client assertion subject is missing");
            return null;
        }
        var idpStorage = context.getSession().identityProviders();

        return context.getRealm().getClientsStream()
            .filter(c -> subject.equals(c.getAttribute(FederatedJWTClientAuthenticator.JWT_CREDENTIAL_SUBJECT_KEY)))
            .map(matchingClient -> {
                String idpAlias = matchingClient.getAttribute(FederatedJWTClientAuthenticator.JWT_CREDENTIAL_ISSUER_KEY);
                if (idpAlias == null) {
                    return null;
                }

                IdentityProviderModel idp = idpStorage.getByAlias(idpAlias);
                if (idp != null
                        && UDSKubernetesIdentityProviderFactory.PROVIDER_ID.equals(idp.getProviderId())
                        && idp.isEnabled()) {
                    LOGGER.debugf("Fallback lookup matched client '%s' to IdP '%s'",
                        matchingClient.getClientId(), idpAlias);
                    return new LookupResult(matchingClient, idp);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
}
