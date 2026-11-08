/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.TokenIntrospectionTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.urls.UrlType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keycloak 26.8.1 lacks the native Admin API audience mapper. This resolves the audience from each frontend request
 * while UDS Core leaves {@code KC_HOSTNAME} unset; the native mapper can replace it after 26.8.1 support is removed.
 */
public class UDSAdminApiAudienceMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, TokenIntrospectionTokenMapper {

    public static final String PROVIDER_ID = "uds-admin-api-audience-mapper";

    /** {@inheritDoc} */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayType() {
        return "UDS Admin API Audience";
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    /** {@inheritDoc} */
    @Override
    public String getHelpText() {
        return "Adds the request's realm Admin API audience to the token.";
    }

    /** {@inheritDoc} */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        List<ProviderConfigProperty> properties = new ArrayList<>();
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(properties, UDSAdminApiAudienceMapper.class);
        properties.removeIf(property -> OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN.equals(property.getName()));
        return properties;
    }

    /** {@inheritDoc} */
    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession,
                            KeycloakSession session, ClientSessionContext clientSessionCtx) {
        token.addAudience(getAdminApiAudience(session, session.getContext().getRealm()));
    }

    /** Resolves the realm Admin API URL from the current frontend request, including dynamic hostnames. */
    public static String getAdminApiAudience(KeycloakSession session, RealmModel realm) {
        return AdminRoot.realmsUrl(session.getContext().getUri(UrlType.FRONTEND).getBaseUriBuilder())
                .path(realm.getName())
                .build()
                .toString();
    }

    /** Builds a mapper enabled for ordinary, lightweight, and introspected access tokens. */
    public static ProtocolMapperModel createMapper(String name) {
        ProtocolMapperModel mapper = new ProtocolMapperModel();
        mapper.setName(name);
        mapper.setProtocolMapper(PROVIDER_ID);
        mapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        Map<String, String> config = new HashMap<>();
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_INTROSPECTION, "true");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_LIGHTWEIGHT_ACCESS_TOKEN, "true");
        mapper.setConfig(config);
        return mapper;
    }
}
