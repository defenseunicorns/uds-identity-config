/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType.ASTChoiceType;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.mappers.AbstractSAMLProtocolMapper;
import org.keycloak.protocol.saml.mappers.AttributeStatementHelper;
import org.keycloak.protocol.saml.mappers.SAMLAttributeStatementMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CustomAWSSAMLGroupMapper extends AbstractSAMLProtocolMapper implements SAMLAttributeStatementMapper {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String PROVIDER_ID = "aws-saml-group-mapper";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(AttributeStatementHelper.SAML_ATTRIBUTE_NAME);
        property.setLabel("Group attribute name");
        property.setDefaultValue("member");
        property.setHelpText(
                "Name of the SAML attribute you want to put your groups into.  i.e. 'member', 'memberOf'.");
        configProperties.add(property);
        property = new ProviderConfigProperty();
        property.setName(AttributeStatementHelper.FRIENDLY_NAME);
        property.setLabel(AttributeStatementHelper.FRIENDLY_NAME_LABEL);
        property.setHelpText(AttributeStatementHelper.FRIENDLY_NAME_HELP_TEXT);
        configProperties.add(property);
        property = new ProviderConfigProperty();
        property.setName(AttributeStatementHelper.SAML_ATTRIBUTE_NAMEFORMAT);
        property.setLabel("SAML Attribute NameFormat");
        property.setHelpText("SAML Attribute NameFormat.  Can be basic, URI reference, or unspecified.");
        List<String> types = new ArrayList<String>(3);
        types.add(AttributeStatementHelper.BASIC);
        types.add(AttributeStatementHelper.URI_REFERENCE);
        types.add(AttributeStatementHelper.UNSPECIFIED);
        property.setType(ProviderConfigProperty.LIST_TYPE);
        property.setOptions(types);
        configProperties.add(property);
    }

    @Override
    public String getDisplayCategory() {
        return "AWS Group Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Custom AWS SAML Group Mapper";
    }

    @Override
    public String getHelpText() {
        return "Format groups for use with AWS authentication in this format: `group1:group2:group3`";
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
        UserModel user = userSession.getUser();

        // Retrieve the user's groups
        Set<GroupModel> groups = user.getGroupsStream().collect(Collectors.toSet());

        // Filter groups to only include those whose full group path contains "-aws-"
        List<String> groupPaths = groups.stream()
                .map(ModelToRepresentation::buildGroupPath)
                .filter(groupPath -> groupPath.contains("-aws-"))
                .collect(Collectors.toList());

        if (!groupPaths.isEmpty()) {
            List<String> sanitizedGroupPaths = groupPaths.stream()
                .filter(groupPath -> {
                    if (groupPath.contains(":")) {
                        logger.warn("Skipping group path because it contains ':': {}", groupPath);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

            if (sanitizedGroupPaths.isEmpty()) {
                return;
            }

            String groupsString = String.join(":", sanitizedGroupPaths);

            // Create a new SAML attribute
            AttributeType attribute = new AttributeType(
                    mappingModel.getConfig().get(AttributeStatementHelper.SAML_ATTRIBUTE_NAME));
            attribute.setNameFormat(JBossSAMLURIConstants.ATTRIBUTE_FORMAT_BASIC.get());
            attribute.addAttributeValue(groupsString);
            attributeStatement.addAttribute(new ASTChoiceType(attribute));
        }
    }
}
