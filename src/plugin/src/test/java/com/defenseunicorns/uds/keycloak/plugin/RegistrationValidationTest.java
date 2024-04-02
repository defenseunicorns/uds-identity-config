package com.defenseunicorns.uds.keycloak.plugin;

import org.apache.commons.io.FilenameUtils;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationPage;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.yaml.snakeyaml.Yaml;

import com.defenseunicorns.uds.keycloak.plugin.utils.NewObjectProvider;
import com.defenseunicorns.uds.keycloak.plugin.utils.ValidationUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.stream.Collectors;

import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupFileMocks;
import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupX509Mocks;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Yaml.class, FileInputStream.class, File.class, X509Tools.class, FilenameUtils.class, NewObjectProvider.class })
@PowerMockIgnore("javax.management.*")
public class RegistrationValidationTest {

    @Before
    public void setup() throws Exception {
        setupX509Mocks();
        setupFileMocks();
    }


    @Test
    public void testInvalidFields() {
        String[] errorEvent = new String[1];
        List<FormMessage> errors = new ArrayList<>();
        MultivaluedMapImpl<String, String> valueMap = new MultivaluedMapImpl<>();
        ValidationContext context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);
        RegistrationValidation validation = new RegistrationValidation();
        validation.validate(context);
        Assert.assertEquals(errorEvent[0], Errors.INVALID_REGISTRATION);
        Set<String> errorFields = errors.stream().map(FormMessage::getField).collect(Collectors.toSet());

        Assert.assertTrue(errorFields.contains("firstName"));
        Assert.assertTrue(errorFields.contains("lastName"));
        Assert.assertTrue(errorFields.contains("username"));
        Assert.assertTrue(errorFields.contains("user.attributes.affiliation"));
        Assert.assertTrue(errorFields.contains("user.attributes.rank"));
        Assert.assertTrue(errorFields.contains("user.attributes.organization"));
        Assert.assertTrue(errorFields.contains("email"));
        Assert.assertEquals(7, errors.size());
    }

    @Test
    public void testEmailValidation() {
        String[] errorEvent = new String[1];
        List<FormMessage> errors = new ArrayList<>();
        MultivaluedMapImpl<String, String> valueMap = new MultivaluedMapImpl<>();
        valueMap.putSingle("firstName", "Jone");
        valueMap.putSingle("lastName", "Doe");
        valueMap.putSingle("username", "tester");
        valueMap.putSingle("user.attributes.affiliation", "AF");
        valueMap.putSingle("user.attributes.rank", "E2");
        valueMap.putSingle("user.attributes.organization", "Com");
        valueMap.putSingle("user.attributes.location", "42");
        valueMap.putSingle("email", "test@gmail.com");

        ValidationContext context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

        RegistrationValidation validation = new RegistrationValidation();
        validation.validate(context);
        Assert.assertEquals(0, errors.size());

        // test an email address already in use
        valueMap.putSingle("email", "test@ss.usafa.edu");
        errorEvent = new String[1];
        errors = new ArrayList<>();
        context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

        validation = new RegistrationValidation();
        validation.validate(context);
        Assert.assertEquals(Errors.EMAIL_IN_USE, errorEvent[0]);
        Assert.assertEquals(1, errors.size());
        Assert.assertEquals(RegistrationPage.FIELD_EMAIL, errors.get(0).getField());

    }

    @Test
    public void testGroupAutoJoinByEmail() {
        String[] errorEvent = new String[1];
        List<FormMessage> errors = new ArrayList<>();
        MultivaluedMapImpl<String, String> valueMap = new MultivaluedMapImpl<>();
        valueMap.putSingle("firstName", "Jone");
        valueMap.putSingle("lastName", "Doe");
        valueMap.putSingle("username", "tester");
        valueMap.putSingle("user.attributes.affiliation", "AF");
        valueMap.putSingle("user.attributes.rank", "E2");
        valueMap.putSingle("user.attributes.organization", "Com");
        valueMap.putSingle("user.attributes.location", "42");
        valueMap.putSingle("email", "test@gmail.com");

        ValidationContext context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

        RegistrationValidation validation = new RegistrationValidation();
        validation.validate(context);
        Assert.assertEquals(0, errors.size());

        // test valid IL2 email with custom domains
        valueMap.putSingle("email", "rando@supercool.unicorns.com");
        errorEvent = new String[1];
        errors = new ArrayList<>();
        context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

        validation = new RegistrationValidation();
        validation.validate(context);
        Assert.assertNull(errorEvent[0]);
        Assert.assertEquals(0, errors.size());

        // test valid IL4 email with custom domains
        valueMap.putSingle("email", "test22@ss.usafa.edu");
        errorEvent = new String[1];
        errors = new ArrayList<>();
        context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

        validation = new RegistrationValidation();
        validation.validate(context);
        Assert.assertNull(errorEvent[0]);
        Assert.assertEquals(0, errors.size());

        // Test existing x509 registration
        errorEvent = new String[1];
        errors = new ArrayList<>();
        context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

        PowerMockito.when(X509Tools.isX509Registered(any(FormContext.class))).thenReturn(true);

        validation = new RegistrationValidation();
        validation.validate(context);
        Assert.assertEquals(Errors.INVALID_REGISTRATION, errorEvent[0]);
    }

    @Test
    public void testSuccess() {
    }

    @Test
    public void testBuildPage() {
        RegistrationValidation subject = new RegistrationValidation();
        FormContext context = mock(FormContext.class);
        LoginFormsProvider form = mock(LoginFormsProvider.class);
        subject.buildPage(context, form);

        verify(form).setAttribute("cacIdentity", "thing");
    }

    @Test
    public void testGetDisplayType() {
        RegistrationValidation subject = new RegistrationValidation();
        Assert.assertEquals(subject.getDisplayType(), "UDS Registration Validation");
    }

    @Test
    public void testGetId() {
        RegistrationValidation subject = new RegistrationValidation();
        Assert.assertEquals(subject.getId(), "registration-validation-action");
    }

    @Test
    public void testIsConfigurable() {
        RegistrationValidation subject = new RegistrationValidation();
        Assert.assertFalse(subject.isConfigurable());
    }

    @Test
    public void testGetRequirementChoices() {
        RegistrationValidation subject = new RegistrationValidation();
        AuthenticationExecutionModel.Requirement[] expected = { AuthenticationExecutionModel.Requirement.REQUIRED };
        assertArrayEquals(expected, subject.getRequirementChoices());
    }
}
