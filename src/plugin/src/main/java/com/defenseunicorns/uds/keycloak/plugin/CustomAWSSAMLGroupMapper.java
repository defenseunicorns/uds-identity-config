package com.defenseunicorns.uds.keycloak.plugin;

import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType.ASTChoiceType;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.mappers.AbstractSAMLProtocolMapper;
import org.keycloak.protocol.saml.mappers.SAMLAttributeStatementMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomAWSSAMLGroupMapper extends AbstractSAMLProtocolMapper implements SAMLAttributeStatementMapper {

    public static final String PROVIDER_ID = "aws-saml-group-mapper";
    private static final String AWS_GROUPS_CLAIM = "aws-groups";

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
        return new ArrayList<>();
    }

    @Override
    public void transformAttributeStatement(AttributeStatementType attributeStatement, ProtocolMapperModel mappingModel,
                                            KeycloakSession session, UserSessionModel userSession, AuthenticatedClientSessionModel clientSession) {
        UserModel user = userSession.getUser();

        // Collect the user's groups into a list
        List<String> groups = user.getGroupsStream() != null
                              ? user.getGroupsStream().map(groupModel -> groupModel.getName()).collect(Collectors.toList())
                              : new ArrayList<>();

        // Only proceed if there are groups
        if (!groups.isEmpty()) {
            // Concatenate the group names into a single string separated by colons
            String groupsString = String.join(":", groups);

            // Create a new SAML attribute
            AttributeType attribute = new AttributeType(AWS_GROUPS_CLAIM);
            attribute.setNameFormat(JBossSAMLURIConstants.ATTRIBUTE_FORMAT_BASIC.get());

            // Add the concatenated groups string as the attribute value
            attribute.addAttributeValue(groupsString);

            attributeStatement.addAttribute(new ASTChoiceType(attribute));
        }
    }
}
