// src/test/java/com/defenseunicorns/uds/keycloak/plugin/CustomAWSSAMLGroupMapperTest.java

package com.defenseunicorns.uds.keycloak.plugin;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.GroupModel;
import org.mockito.Mockito;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType.ASTChoiceType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CustomAWSSAMLGroupMapperTest {

    private CustomAWSSAMLGroupMapper mapper;
    private KeycloakSession mockSession;
    private UserSessionModel mockUserSession;
    private AuthenticatedClientSessionModel mockClientSession;
    private UserModel mockUser;
    private AttributeStatementType mockAttributeStatement;

    @Before
    public void setup() {
        mapper = new CustomAWSSAMLGroupMapper();
        mockSession = Mockito.mock(KeycloakSession.class);
        mockUserSession = Mockito.mock(UserSessionModel.class);
        mockClientSession = Mockito.mock(AuthenticatedClientSessionModel.class);
        mockUser = Mockito.mock(UserModel.class);
        mockAttributeStatement = Mockito.mock(AttributeStatementType.class);

        when(mockUserSession.getUser()).thenReturn(mockUser);
    }

    @Test
    public void testTransformAttributeStatement_singleGroup() {
        GroupModel group1 = Mockito.mock(GroupModel.class);
        when(group1.getName()).thenReturn("group1");

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1));

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession,
                mockClientSession);

        verify(mockAttributeStatement).addAttribute(Mockito.argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();

            return attrType.getName().equals("aws-groups") &&
                    attrType.getAttributeValue().contains("group1");
        }));
    }

    @Test
    public void testTransformAttributeStatement_multipleGroups() {
        GroupModel group1 = Mockito.mock(GroupModel.class);
        GroupModel group2 = Mockito.mock(GroupModel.class);
        when(group1.getName()).thenReturn("group1");
        when(group2.getName()).thenReturn("group2");

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1, group2));

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession,
                mockClientSession);

        verify(mockAttributeStatement).addAttribute(Mockito.argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();

            return attrType.getName().equals("aws-groups") &&
                    attrType.getAttributeValue().contains("group1:group2");
        }));
    }

    @Test
    public void testTransformAttributeStatement_slashedMultipleGroups() {
        GroupModel group1 = Mockito.mock(GroupModel.class);
        GroupModel group2 = Mockito.mock(GroupModel.class);
        when(group1.getName()).thenReturn("/group1/group2");
        when(group2.getName()).thenReturn("/group1/group3");

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1, group2));

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession,
                mockClientSession);

        verify(mockAttributeStatement).addAttribute(Mockito.argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();

            return attrType.getName().equals("aws-groups") &&
                    attrType.getAttributeValue().contains("/group1/group2:/group1/group3");
        }));
    }

    @Test
    public void testTransformAttributeStatement_noGroups() {
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement, never()).addAttribute(any(ASTChoiceType.class));
    }

    @Test
    public void testTransformAttributeStatement_nullGroups() {
        when(mockUser.getGroupsStream()).thenReturn(null);

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement, never()).addAttribute(any(ASTChoiceType.class));
    }
}
