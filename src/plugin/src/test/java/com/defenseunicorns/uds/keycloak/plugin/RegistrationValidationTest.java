/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.utils.ValidationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.utils.FormMessage;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupFileMocks;
import static com.defenseunicorns.uds.keycloak.plugin.utils.Utils.setupX509Mocks;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ FileInputStream.class, File.class, X509Tools.class })
@PowerMockIgnore("javax.management.*")
public class RegistrationValidationTest {

    @Before
    public void setup() throws Exception {
        setupX509Mocks();
        setupFileMocks();
    }

    @Test
    public void testGroupAutoJoinByEmail() {
        String[] errorEvent = new String[1];
        List<FormMessage> errors = new ArrayList<>();
        Map<String, List<String>> valueMap = new HashMap<>();

        // Populate the valueMap with test data
        valueMap.put("firstName", List.of("Jone"));
        valueMap.put("lastName", List.of("Doe"));
        valueMap.put("username", List.of("tester"));
        valueMap.put("affiliation", List.of("AF"));
        valueMap.put("rank", List.of("E2"));
        valueMap.put("organization", List.of("Com"));
        valueMap.put("location", List.of("42"));
        valueMap.put("email", List.of("test@gmail.com"));

        // Set up your test context
        ValidationContext context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);
        RegistrationValidation validation = new RegistrationValidation();
        validation.validate(context);

        // Assert the validation result for the first set of values
        Assert.assertEquals(0, errors.size());

        // Test valid IL2 email with custom domains
        valueMap.put("email", List.of("rando@supercool.unicorns.com"));
        errorEvent = new String[1];
        errors = new ArrayList<>();
        context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);
        validation = new RegistrationValidation();
        validation.validate(context);

        // Assert the validation result for the second set of values
        Assert.assertNull(errorEvent[0]);
        Assert.assertEquals(0, errors.size());

        // Test valid IL4 email with custom domains
        valueMap.put("email", List.of("test22@ss.usafa.edu"));
        errorEvent = new String[1];
        errors = new ArrayList<>();
        context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);
        validation = new RegistrationValidation();
        validation.validate(context);

        // Assert the validation result for the third set of values
        Assert.assertNull(errorEvent[0]);
        Assert.assertEquals(0, errors.size());

        // Test existing X509 registration
        errorEvent = new String[1];
        errors = new ArrayList<>();
        context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

        // Mock the behavior of X509Tools
        PowerMockito.when(X509Tools.isX509Registered(any(FormContext.class))).thenReturn(true);
        validation = new RegistrationValidation();
        validation.validate(context);

        // Assert the validation result for the fourth set of values
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

        // Mock the X509Tools to return a non-null value for getX509SubjectDN
        PowerMockito.mockStatic(X509Tools.class);
        when(X509Tools.getX509SubjectDN(any(FormContext.class))).thenReturn("subjectDN");

        subject.buildPage(context, form);

        // Verify that the setAttribute method was called with the expected arguments
        verify(form).setAttribute("cacSubjectDN", "subjectDN");
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
