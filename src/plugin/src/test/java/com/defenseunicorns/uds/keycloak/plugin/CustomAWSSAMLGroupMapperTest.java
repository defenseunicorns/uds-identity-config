/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.junit.Before;
import org.junit.Test;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

public class CustomAWSSAMLGroupMapperTest {

    private CustomAWSSAMLGroupMapper mapper;
    private KeycloakSession mockSession;
    private UserSessionModel mockUserSession;
    private AuthenticatedClientSessionModel mockClientSession;
    private UserModel mockUser;
    private AttributeStatementType mockAttributeStatement;
    private ProtocolMapperModel mockMappingModel;
    private Map<String, String> config;

    @Before
    public void setup() {
        mapper = new CustomAWSSAMLGroupMapper();
        mockSession = Mockito.mock(KeycloakSession.class);
        mockUserSession = Mockito.mock(UserSessionModel.class);
        mockClientSession = Mockito.mock(AuthenticatedClientSessionModel.class);
        mockUser = Mockito.mock(UserModel.class);
        mockAttributeStatement = Mockito.mock(AttributeStatementType.class);
        mockMappingModel = Mockito.mock(ProtocolMapperModel.class);

        when(mockUserSession.getUser()).thenReturn(mockUser);

        // Use a config that sets the attribute name to "aws-groups"
        config = new HashMap<>();
        config.put(AttributeStatementHelper.SAML_ATTRIBUTE_NAME, "aws-groups");
        when(mockMappingModel.getConfig()).thenReturn(config);
    }

    @Test
    public void testTransformAttributeStatement_singleGroupNoParent() {
        GroupModel group = mock(GroupModel.class);
        // Must include "-aws-" to pass the filter.
        when(group.getName()).thenReturn("Admin-aws-test");
        when(group.getParent()).thenReturn(null);

        // For testing, assume ModelToRepresentation.buildGroupPath returns "/" +
        // group.getName(),
        // so here it returns "/Admin-aws-test".
        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Expect the attribute value to equal "/Admin-aws-test"
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "aws-groups".equals(attrType.getName()) && value.equals("/Admin-aws-test");
        }));
    }

    @Test
    public void testTransformAttributeStatement_singleGroupParent() {
        GroupModel childGroup = mock(GroupModel.class);
        GroupModel parentGroup = mock(GroupModel.class);

        when(childGroup.getName()).thenReturn("Admin-aws-test");
        when(childGroup.getParent()).thenReturn(parentGroup);

        when(parentGroup.getName()).thenReturn("Core-aws-test");
        when(parentGroup.getParent()).thenReturn(null);

        // Assume that for a child group the built group path is the concatenation of
        // the parent's and child's names:
        // i.e. "/Core-aws-test/Admin-aws-test"
        when(mockUser.getGroupsStream()).thenReturn(Stream.of(childGroup));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "aws-groups".equals(attrType.getName())
                    && value.equals("/Core-aws-test/Admin-aws-test");
        }));
    }

    @Test
    public void testTransformAttributeStatement_groupNameWithColonThrowsException() {
        GroupModel group = mock(GroupModel.class);
        // Group name contains "-aws-" and also a colon to trigger the exception.
        when(group.getName()).thenReturn("Admin-aws-IT:Test");
        when(group.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group));

        try {
            mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                    mockSession, mockUserSession, mockClientSession);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            // Expected exception message
            assertEquals("Group name contains invalid character ':'. Group name: /Admin-aws-IT:Test", e.getMessage());
        }
    }

    @Test
    public void testTransformAttributeStatement_multipleGroupsNoParent() {
        GroupModel group1 = mock(GroupModel.class);
        GroupModel group2 = mock(GroupModel.class);

        when(group1.getName()).thenReturn("Admin-aws-test");
        when(group1.getParent()).thenReturn(null);

        when(group2.getName()).thenReturn("Auditor-aws-test");
        when(group2.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1, group2));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Expect the concatenated attribute value to contain both built group paths:
        // "/Admin-aws-test" and "/Auditor-aws-test"
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            if (!(attribute instanceof ASTChoiceType)) {
                return false;
            }
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            if (!"aws-groups".equals(attrType.getName())) {
                return false;
            }
            String value = attrType.getAttributeValue().get(0).toString();
            return value.contains("/Admin-aws-test") && value.contains("/Auditor-aws-test");
        }));
    }

    @Test
    public void testTransformAttributeStatement_multipleGroupsParent() {
        GroupModel childGroup1 = mock(GroupModel.class);
        GroupModel childGroup2 = mock(GroupModel.class);
        GroupModel parentGroup = mock(GroupModel.class);

        when(childGroup1.getName()).thenReturn("Admin-aws-test");
        when(childGroup1.getParent()).thenReturn(parentGroup);

        when(childGroup2.getName()).thenReturn("Auditor-aws-test");
        when(childGroup2.getParent()).thenReturn(parentGroup);

        when(parentGroup.getName()).thenReturn("Core-aws-test");
        when(parentGroup.getParent()).thenReturn(null);

        // Assume that each child's full group path is built by concatenating parent's
        // path with child's name:
        // i.e. "/Core-aws-test/Admin-aws-test" and "/Core-aws-test/Auditor-aws-test"
        when(mockUser.getGroupsStream()).thenReturn(Stream.of(childGroup1, childGroup2));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            if (!(attribute instanceof ASTChoiceType)) {
                return false;
            }
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            if (!"aws-groups".equals(attrType.getName())) {
                return false;
            }
            String value = attrType.getAttributeValue().get(0).toString();
            return value.contains("/Core-aws-test/Admin-aws-test")
                    && value.contains("/Core-aws-test/Auditor-aws-test");
        }));
    }

    @Test
    public void testTransformAttributeStatement_noGroups() {
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement, never()).addAttribute(any(ASTChoiceType.class));
    }

    @Test
    public void testTransformAttributeStatement_nullGroups() {
        // Simulate a scenario where the user's groups stream is empty.
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement, never()).addAttribute(any(ASTChoiceType.class));
    }

    @Test
    public void testTransformAttributeStatement_mixedGroups() {
        GroupModel awsGroup = mock(GroupModel.class);
        GroupModel nonAwsGroup = mock(GroupModel.class);

        // awsGroup qualifies because it includes "-aws-"
        when(awsGroup.getName()).thenReturn("Admin-aws-test");
        when(awsGroup.getParent()).thenReturn(null);

        // nonAwsGroup does not qualify and should be ignored.
        when(nonAwsGroup.getName()).thenReturn("NonAwsGroup");
        when(nonAwsGroup.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(awsGroup, nonAwsGroup));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Expect only the awsGroup's built path ("/Admin-aws-test") to be in the
        // attribute.
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "aws-groups".equals(attrType.getName())
                    && value.equals("/Admin-aws-test");
        }));
    }

    @Test
    public void testTransformAttributeStatement_childGroupWithNonAwsParent() {
        GroupModel childGroup = mock(GroupModel.class);
        GroupModel nonAwsParent = mock(GroupModel.class);

        // Child has "-aws-" in its name, but parent's name does not.
        when(childGroup.getName()).thenReturn("Admin-aws-test");
        when(childGroup.getParent()).thenReturn(nonAwsParent);

        // Parent does not include "-aws-"
        when(nonAwsParent.getName()).thenReturn("Core");
        when(nonAwsParent.getParent()).thenReturn(null);

        // The built group path is assumed to be "/Core/Admin-aws-test"
        when(mockUser.getGroupsStream()).thenReturn(Stream.of(childGroup));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Since the child's name qualifies, the final built path
        // ("/Core/Admin-aws-test") contains "-aws-"
        // and is expected to be processed.
        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();
            String value = attrType.getAttributeValue().get(0).toString();
            return "aws-groups".equals(attrType.getName())
                    && value.equals("/Core/Admin-aws-test");
        }));
    }

    @Test
    public void testTransformAttributeStatement_singleNonAwsGroup() {
        GroupModel group = mock(GroupModel.class);
        // This group name does not include "-aws-"
        when(group.getName()).thenReturn("Admin");
        when(group.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group));

        mapper.transformAttributeStatement(mockAttributeStatement, mockMappingModel,
                mockSession, mockUserSession, mockClientSession);

        // Verify that addAttribute is never called since the group does not meet the
        // -aws- filter.
        verify(mockAttributeStatement, never()).addAttribute(any(ASTChoiceType.class));
    }
}
