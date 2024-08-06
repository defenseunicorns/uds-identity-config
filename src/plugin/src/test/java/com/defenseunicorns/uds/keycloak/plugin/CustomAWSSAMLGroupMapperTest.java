package com.defenseunicorns.uds.keycloak.plugin;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
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
    public void testTransformAttributeStatement_singleGroupNoParent() {
        GroupModel group = mock(GroupModel.class);
        when(group.getName()).thenReturn("Admin");
        when(group.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group));

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();

            return attrType.getName().equals("aws-groups") && attrType.getAttributeValue().contains("/Admin");
        }));
    }

    @Test
    public void testTransformAttributeStatement_singleGroupParent() {
        GroupModel group1 = mock(GroupModel.class);
        GroupModel group2 = mock(GroupModel.class);

        when(group1.getName()).thenReturn("Admin");
        when(group1.getParent()).thenReturn(group2);

        when(group2.getName()).thenReturn("Core");
        when(group2.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1));

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();

            return attrType.getName().equals("aws-groups") && attrType.getAttributeValue().contains("/Core/Admin");
        }));
    }

    @Test
    public void testTransformAttributeStatement_multipleGroupsNoParent() {
        GroupModel group1 = mock(GroupModel.class);
        GroupModel group2 = mock(GroupModel.class);

        when(group1.getName()).thenReturn("Admin");
        when(group1.getParent()).thenReturn(null);

        when(group2.getName()).thenReturn("Auditor");
        when(group2.getParent()).thenReturn(null);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1, group2));

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            if (!(attribute instanceof ASTChoiceType)) {
                return false;
            }
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();

            if (!attrType.getName().equals("aws-groups")) {
                return false;
            }

            String attributeValue = attrType.getAttributeValue().get(0).toString();
            return attributeValue.contains("/Admin") && attributeValue.contains("/Auditor");
        }));
    }

    @Test
    public void testTransformAttributeStatement_multipleGroupsParent() {
        GroupModel group1 = mock(GroupModel.class);
        GroupModel group2 = mock(GroupModel.class);
        GroupModel group3 = mock(GroupModel.class);

        when(group1.getName()).thenReturn("Admin");
        when(group1.getParent()).thenReturn(group2);

        when(group2.getName()).thenReturn("Core");
        when(group2.getParent()).thenReturn(null);

        when(group3.getName()).thenReturn("Auditor");
        when(group3.getParent()).thenReturn(group2);

        when(mockUser.getGroupsStream()).thenReturn(Stream.of(group1, group3));

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement).addAttribute(argThat(attribute -> {
            if (!(attribute instanceof ASTChoiceType)) {
                return false;
            }
            ASTChoiceType choice = (ASTChoiceType) attribute;
            AttributeType attrType = (AttributeType) choice.getAttribute();

            if (!attrType.getName().equals("aws-groups")) {
                return false;
            }

            String attributeValue = attrType.getAttributeValue().get(0).toString();
            return attributeValue.contains("/Core/Admin") && attributeValue.contains("/Core/Auditor");
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
        when(mockUser.getGroupsStream()).thenReturn(Stream.empty());

        mapper.transformAttributeStatement(mockAttributeStatement, null, mockSession, mockUserSession, mockClientSession);

        verify(mockAttributeStatement, never()).addAttribute(any(ASTChoiceType.class));
    }
}
