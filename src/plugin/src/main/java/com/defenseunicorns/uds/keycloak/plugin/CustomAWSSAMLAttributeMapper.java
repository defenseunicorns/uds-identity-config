/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.saml.mappers.AbstractSAMLProtocolMapper;
import org.keycloak.protocol.saml.mappers.AttributeStatementHelper;
import org.keycloak.protocol.saml.mappers.SAMLAttributeStatementMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CustomAWSSAMLAttributeMapper extends AbstractSAMLProtocolMapper implements SAMLAttributeStatementMapper {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String PROVIDER_ID = "aws-saml-attribute-mapper";
    private static final String AWS_PRINCIPAL_TAG_PREFIX = "https://aws.amazon.com/SAML/Attributes/PrincipalTag:";
    public static final String USER_ATTRIBUTE = "user.attribute";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

    static {
        ProviderConfigProperty property;

        property = new ProviderConfigProperty();
        property.setName(USER_ATTRIBUTE);
        property.setLabel("User Attribute");
        property.setHelpText("Name of the user attribute to map.");
        configProperties.add(property);

        AttributeStatementHelper.setConfigProperties(configProperties);
    }

    @Override
    public String getDisplayCategory() {
        return "AttributeStatement Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Custom AWS SAML Attribute Mapper";
    }

    @Override
    public String getHelpText() {
        return "Maps user attributes to AWS SAML attributes with colon-separated values for multiple attribute values";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public void transformAttributeStatement(AttributeStatementType attributeStatement, ProtocolMapperModel mappingModel,
            KeycloakSession session, UserSessionModel userSession, AuthenticatedClientSessionModel clientSession) {

        String userAttributeName = mappingModel.getConfig().get(USER_ATTRIBUTE);
        String samlAttributeName = mappingModel.getConfig().get(AttributeStatementHelper.SAML_ATTRIBUTE_NAME);

        // Only process if this is an AWS PrincipalTag attribute
        if (samlAttributeName == null || !samlAttributeName.startsWith(AWS_PRINCIPAL_TAG_PREFIX)) {
            return;
        }

        if (userAttributeName == null || userAttributeName.isEmpty()) {
            return;
        }

        UserModel user = userSession.getUser();

        Collection<String> attributeValues = KeycloakModelUtils.resolveAttribute(user, userAttributeName, true);
        List<String> values = attributeValues instanceof List ? (List<String>) attributeValues : new ArrayList<>(attributeValues);

        if (!values.isEmpty()) {
            List<String> sanitizedValues = values.stream()
                .filter(value -> {
                    if (value.contains(":")) {
                        logger.warn("Skipping value for attribute '{}' because it contains ':': {}", userAttributeName, value);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

            if (sanitizedValues.isEmpty()) {
                return;
            }

            String concatenatedValue = String.join(":", sanitizedValues);
            AttributeStatementHelper.addAttribute(attributeStatement, mappingModel, concatenatedValue);
        }
    }
}
