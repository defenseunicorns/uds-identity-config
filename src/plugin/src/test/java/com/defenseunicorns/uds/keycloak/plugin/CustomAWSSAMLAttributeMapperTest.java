/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.mappers.AttributeStatementHelper;
import org.mockito.Mockito;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType.ASTChoiceType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

public class CustomAWSSAMLAttributeMapperTest {

    private CustomAWSSAMLAttributeMapper mapper;
    private KeycloakSession mockSession;
    private UserSessionModel mockUserSession;
    private AuthenticatedClientSessionModel mockClientSession;
    private UserModel mockUser;
    private AttributeStatementType mockAttributeStatement;
    private ProtocolMapperModel mockMappingModel;
    private Map<String, String> config;

    @BeforeEach
    public void setup() {
        mapper = new CustomAWSSAMLAttributeMapper();
        mockSession = Mockito.mock(KeycloakSession.class);
        mockUserSession = Mockito.mock(UserSessionModel.class);
        mockClientSession = Mockito.mock(AuthenticatedClientSessionModel.class);
        mockUser = Mockito.mock(UserModel.class);
        mockAttributeStatement = Mockito.mock(AttributeStatementType.class);
        mockMappingModel = Mockito.mock(ProtocolMapperModel.class);

        when(mockUserSession.getUser()).thenReturn(mockUser);

        // Use a config that sets the user attribute and SAML attribute name for AWS PrincipalTag
        config = new HashMap<>();
        config.put(CustomAWSSAMLAttributeMapper.USER_ATTRIBUTE, "department");
        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME,
                "https://aws.amazon.com/SAML/Attributes/PrincipalTag:department");
        when(mockMappingModel.getConfig()).thenReturn(config);
    }

    @Test
    public void testTransformAttributeStatement_awsPrincipalTagAttribute() {
        // User has department attribute
        when(mockUser.getAttributeStream("department")).thenReturn(Stream.of("engineering"));
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was added with correct value
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "https://aws.amazon.com/SAML/Attributes/PrincipalTag:department".equals(attrType.getName())
                && "engineering".equals(value);
        }));
    }

    @Test
    public void testTransformAttributeStatement_nonAwsAttributeSkipped() {
        // Configure for non-AWS attribute (missing PrincipalTag prefix)
        config.put(CustomAWSSAMLAttributeMapper.USER_ATTRIBUTE, "department");
        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME, "department");

        when(mockUser.getAttributeStream("department")).thenReturn(Stream.of("engineering"));
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was NOT added because it's not an AWS PrincipalTag
        verify(mockAttributeStatement, never()).addAttribute(any());
    }

    @Test
    public void testTransformAttributeStatement_multipleValuesFromGroups() {
        GroupModel group1 = mock(GroupModel.class);
        GroupModel group2 = mock(GroupModel.class);

        // User has no direct department attribute
        when(mockUser.getAttributeStream("department")).thenReturn(Stream.empty());

        // Groups have department attributes
        when(group1.getAttributeStream("department")).thenReturn(Stream.of("engineering"));
        when(group1.getParent()).thenReturn(null);

        when(group2.getAttributeStream("department")).thenReturn(Stream.of("platform"));
        when(group2.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1, group2));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was added with both values colon-separated
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "https://aws.amazon.com/SAML/Attributes/PrincipalTag:department".equals(attrType.getName())
                && value.contains("engineering")
                && value.contains("platform")
                && value.contains(":");
        }));
    }

    @Test
    public void testTransformAttributeStatement_userAndGroupAttributes() {
        config.put(CustomAWSSAMLAttributeMapper.USER_ATTRIBUTE, "title");
        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME,
                "https://aws.amazon.com/SAML/Attributes/PrincipalTag:title");

        GroupModel group = mock(GroupModel.class);

        // User has title attribute
        when(mockUser.getAttributeStream("title")).thenReturn(Stream.of("engineer"));

        // Group also has title attribute
        when(group.getAttributeStream("title")).thenReturn(Stream.of("admin"));
        when(group.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was added with both user and group values colon-separated
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "https://aws.amazon.com/SAML/Attributes/PrincipalTag:title".equals(attrType.getName())
                && value.contains("engineer")
                && value.contains("admin")
                && value.contains(":");
        }));
    }

    @Test
    public void testTransformAttributeStatement_attributeWithColonSkipped() {
        // User has attribute with colon (invalid for AWS)
        when(mockUser.getAttributeStream("department")).thenReturn(Stream.of("engineering:platform"));
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was NOT added because value contains colon
        verify(mockAttributeStatement, never()).addAttribute(any());
    }

    @Test
    public void testTransformAttributeStatement_emptyAttributeValues() {
        // User has no department attribute
        when(mockUser.getAttributeStream("department")).thenReturn(Stream.empty());
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was NOT added because there are no values
        verify(mockAttributeStatement, never()).addAttribute(any());
    }

    @Test
    public void testTransformAttributeStatement_nullUserAttribute() {
        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME,
                "https://aws.amazon.com/SAML/Attributes/PrincipalTag:department");
        // user.attribute is not set

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was NOT added because user.attribute is missing
        verify(mockAttributeStatement, never()).addAttribute(any());
    }

    @Test
    public void testTransformAttributeStatement_emptyUserAttribute() {
        config.put(CustomAWSSAMLAttributeMapper.USER_ATTRIBUTE, "");
        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME,
                "https://aws.amazon.com/SAML/Attributes/PrincipalTag:department");

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was NOT added because user.attribute is empty
        verify(mockAttributeStatement, never()).addAttribute(any());
    }

    @Test
    public void testTransformAttributeStatement_nullSamlAttributeName() {
        config.put(CustomAWSSAMLAttributeMapper.USER_ATTRIBUTE, "department");
        config.remove(AttributeStatementHelper.SAML_ATTRIBUTE_NAME);
        // SAML_ATTRIBUTE_NAME is not set

        when(mockUser.getAttributeStream("department")).thenReturn(Stream.of("engineering"));
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was NOT added because SAML attribute name is missing
        verify(mockAttributeStatement, never()).addAttribute(any());
    }

    @Test
    public void testTransformAttributeStatement_parentGroupAttributes() {
        config.put(CustomAWSSAMLAttributeMapper.USER_ATTRIBUTE, "costCenter");
        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME,
                "https://aws.amazon.com/SAML/Attributes/PrincipalTag:costCenter");

        GroupModel childGroup = mock(GroupModel.class);
        GroupModel parentGroup = mock(GroupModel.class);

        // User has no direct costCenter attribute
        when(mockUser.getAttributeStream("costCenter")).thenReturn(Stream.empty());

        // Child group has costCenter attribute
        when(childGroup.getAttributeStream("costCenter")).thenReturn(Stream.of("cc-001"));
        when(childGroup.getParent()).thenReturn(parentGroup);
        when(childGroup.getParentId()).thenReturn("parent-id");

        // Parent group also has costCenter attribute
        when(parentGroup.getAttributeStream("costCenter")).thenReturn(Stream.of("cc-parent"));
        when(parentGroup.getParent()).thenReturn(null);
        when(parentGroup.getParentId()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(childGroup));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify attribute was added with values from child and parent groups colon-separated
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "https://aws.amazon.com/SAML/Attributes/PrincipalTag:costCenter".equals(attrType.getName())
                && value.contains("cc-001")
                && value.contains("cc-parent")
                && value.contains(":");
        }));
    }
}
