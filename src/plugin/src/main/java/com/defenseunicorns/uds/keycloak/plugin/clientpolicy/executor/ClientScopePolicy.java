/*
 * Copyright 2024-2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.defenseunicorns.uds.keycloak.plugin.CustomGroupPathMapper;
import com.defenseunicorns.uds.keycloak.plugin.StringUtils;
import org.keycloak.events.Errors;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.keycloak.common.util.CollectionUtil.isNotEmpty;

/** Resolves and validates allowed scopes while preserving existing default admin API assignments. */
final class ClientScopePolicy {

    private static final String ADMIN_API_SCOPE = "kc-admin-api";

    static final List<String> DEFAULT_ALLOWED_CLIENT_SCOPES = List.of(
            "openid",
            "aws-groups",
            CustomGroupPathMapper.GROUPS_CLAIM,
            CustomGroupPathMapper.BARE_GROUPS_CLAIM,
            "mapper-saml-username-name",
            "mapper-saml-email-email",
            "mapper-saml-username-login",
            "mapper-saml-firstname-first_name",
            "mapper-saml-lastname-last_name",
            "mapper-saml-grouplist-groups",
            "mapper-oidc-username-username",
            "mapper-oidc-mattermostid-id",
            "mapper-oidc-email-email"
    );

    /** Prevents construction of this stateless helper. */
    private ClientScopePolicy() {}

    /** Resolves built-in, realm-default, and explicitly configured scopes. */
    static List<String> resolveAllowed(UDSClientPolicyPermissionsExecutorConfiguration config, KeycloakSession session) {
        Set<String> allowedClientScopes = new HashSet<>();
        if (config.isUseDefaultAllowedClientScopes()) {
            allowedClientScopes.addAll(DEFAULT_ALLOWED_CLIENT_SCOPES);
            // Add Realm defaults
            allowedClientScopes.addAll(session.getContext().getRealm().getDefaultClientScopesStream(true).map(ClientScopeModel::getName).collect(Collectors.toList()));
        }
        if (StringUtils.isNotBlank(config.getAllowedClientScopesAsString()))
            allowedClientScopes.addAll(StringUtils.parseCommaSeparatedStringToList(config.getAllowedClientScopesAsString()));
        if (config.getAllowedClientScopes() != null)
            allowedClientScopes.addAll(config.getAllowedClientScopes());
        return new ArrayList<>(allowedClientScopes);
    }

    /** Checks an existing default assignment; callers must authorize the update first. */
    static boolean hasAdminApiScope(ClientModel client) {
        return client.getClientScopes(true).containsKey(ADMIN_API_SCOPE);
    }

    /** Allows retention only as a default scope; optional scopes always use the allowlist. */
    static void validate(ClientRepresentation rep, List<String> allowed, boolean retainAdminApiScope) throws ClientPolicyException {
        if (allowed != null) {
            if (isNotEmpty(rep.getDefaultClientScopes())) {
                for (String scope : rep.getDefaultClientScopes()) {
                    if (!(retainAdminApiScope && ADMIN_API_SCOPE.equals(scope)) && !allowed.contains(scope)) {
                        throw new ClientPolicyException(Errors.INVALID_CLIENT, "The Client Scope " + scope + " is not allowed. Rejecting request.");
                    }
                }
            }
            if (isNotEmpty(rep.getOptionalClientScopes())) {
                for (String scope : rep.getOptionalClientScopes()) {
                    if (!allowed.contains(scope)) {
                        throw new ClientPolicyException(Errors.INVALID_CLIENT, "The Client Scope " + scope + " is not allowed. Rejecting request.");
                    }
                }
            }
        }
    }
}
