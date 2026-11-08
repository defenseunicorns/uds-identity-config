/*
 * Copyright 2024-2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.defenseunicorns.uds.keycloak.plugin.CustomAWSSAMLGroupMapper;
import com.defenseunicorns.uds.keycloak.plugin.StringUtils;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.saml.mappers.GroupMembershipMapper;
import org.keycloak.protocol.oidc.mappers.UserAttributeMapper;
import org.keycloak.protocol.oidc.mappers.AudienceProtocolMapper;
import org.keycloak.protocol.oidc.mappers.UserPropertyMapper;
import org.keycloak.protocol.oidc.mappers.FullNameMapper;
import org.keycloak.protocol.oidc.mappers.AddressMapper;
import org.keycloak.protocol.oidc.mappers.SHA256PairwiseSubMapper;
import org.keycloak.protocol.saml.mappers.RoleListMapper;
import org.keycloak.protocol.saml.mappers.UserAttributeStatementMapper;
import org.keycloak.protocol.saml.mappers.UserPropertyAttributeStatementMapper;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.admin.ClientProtocolMapperContext;
import org.keycloak.services.clientpolicy.context.admin.ClientProtocolMapperRegisterContext;
import org.keycloak.services.clientpolicy.context.admin.ClientProtocolMapperUpdateContext;
import org.keycloak.utils.JsonUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.keycloak.common.util.CollectionUtil.isNotEmpty;

/** Resolves and validates mapper types and claims without making authorization decisions. */
final class ProtocolMapperPolicy {

    static final List<String> DEFAULT_ALLOWED_PROTOCOL_MAPPERS = List.of(
            UserAttributeStatementMapper.PROVIDER_ID,
            UserAttributeMapper.PROVIDER_ID,
            UserPropertyAttributeStatementMapper.PROVIDER_ID,
            GroupMembershipMapper.PROVIDER_ID,
            AudienceProtocolMapper.PROVIDER_ID,
            UserPropertyMapper.PROVIDER_ID,
            FullNameMapper.PROVIDER_ID,
            AddressMapper.PROVIDER_ID,
            new SHA256PairwiseSubMapper().getId(),
            RoleListMapper.PROVIDER_ID,
            CustomAWSSAMLGroupMapper.PROVIDER_ID,
            org.keycloak.protocol.oidc.mappers.GroupMembershipMapper.PROVIDER_ID
    );

    private static final Set<String> PROTECTED_TOKEN_CLAIMS = Set.of(
            "sub", "iss", "aud", "azp", "exp", "iat", "nbf", "jti", "typ", "scope", "sid",
            "session_state", "realm_access", "resource_access"
    );

    /** Prevents construction of this stateless helper. */
    private ProtocolMapperPolicy() {}

    /** Resolves configured types, including built-in defaults and registered UDS mappers. */
    static List<String> resolveAllowed(UDSClientPolicyPermissionsExecutorConfiguration config,
                                                               KeycloakSession session) {
        Set<String> allowedProtocolMappers = new HashSet<>();
        if (config.isUseDefaultAllowedProtocolMappers()) {
            List<String> defenseUnicornsProtocolMappers = session.getKeycloakSessionFactory().
                    getProviderFactoriesStream(ProtocolMapper.class)
                    .filter(o -> o.getClass().getPackageName().startsWith("com.defenseunicorns.uds.keycloak"))
                    .map(o -> o.getId())
                    .toList();

            allowedProtocolMappers.addAll(DEFAULT_ALLOWED_PROTOCOL_MAPPERS);
            allowedProtocolMappers.addAll(defenseUnicornsProtocolMappers);
        }
        if (StringUtils.isNotBlank(config.getAllowedProtocolMappersAsString()))
            allowedProtocolMappers.addAll(StringUtils.parseCommaSeparatedStringToList(config.getAllowedProtocolMappersAsString()));
        if (config.getAllowedProtocolMappers() != null)
            allowedProtocolMappers.addAll(config.getAllowedProtocolMappers());
        return new ArrayList<>(allowedProtocolMappers);
    }

    /** Validates proposed claims and existing types; mapper deletion adds no policy restriction. */
    static void validateEvent(ClientProtocolMapperContext context, List<String> allowed) throws ClientPolicyException {
        if (context instanceof ClientProtocolMapperUpdateContext updateContext) {
            validateMapper(updateContext.getProposedProtocolMapper(), allowed);
            validateType(updateContext.getExistingProtocolMapper().getProtocolMapper(), allowed);
        } else if (context instanceof ClientProtocolMapperRegisterContext registerContext
                && registerContext.getProposedProtocolMappers() != null) {
            for (ProtocolMapperRepresentation mapper : registerContext.getProposedProtocolMappers()) {
                validateMapper(mapper, allowed);
            }
        }
    }

    /** Validates embedded client mappers using the same rules as standalone mapper operations. */
    static void validate(List<ProtocolMapperRepresentation> mappers, List<String> allowed) throws ClientPolicyException {
        if (isNotEmpty(mappers)) {
            for (ProtocolMapperRepresentation mapper : mappers) {
                validateMapper(mapper, allowed);
            }
        }
    }

    /** Rejects protected token claims after decoding Keycloak claim-path escapes. */
    private static void validateMapper(ProtocolMapperRepresentation mapper, List<String> allowed) throws ClientPolicyException {
        validateType(mapper.getProtocolMapper(), allowed);
        if (mapper.getConfig() != null) {
            String claimName = mapper.getConfig().get("claim.name");
            if (claimName != null && JsonUtils.splitClaimPath(claimName).stream()
                    .findFirst().filter(PROTECTED_TOKEN_CLAIMS::contains).isPresent()) {
                throw new ClientPolicyException(Errors.INVALID_CLIENT,
                        "The Protocol Mapper cannot overwrite a protected token claim. Rejecting request.");
            }
        }
    }

    /** Checks the configured type allowlist, preserving the unrestricted null configuration. */
    private static void validateType(String mapperType, List<String> allowed) throws ClientPolicyException {
        if (allowed != null && !allowed.contains(mapperType)) {
            throw new ClientPolicyException(Errors.INVALID_CLIENT, "The Protocol Mapper " + mapperType + " is not allowed. Rejecting request.");
        }
    }
}
