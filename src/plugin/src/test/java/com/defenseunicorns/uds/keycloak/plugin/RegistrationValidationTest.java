/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.utils.ValidationUtils;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.utils.FormMessage;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RegistrationValidationTest {

    @Test
    public void testGroupAutoJoinByEmail() {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            // Setup default mock behavior
            x509ToolsMock.when(() -> X509Tools.getX509Username(any(FormContext.class))).thenReturn("thing");
            x509ToolsMock.when(() -> X509Tools.isX509Registered(any(FormContext.class))).thenReturn(false);

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
            assertEquals(0, errors.size());

            // Test valid IL2 email with custom domains
            valueMap.put("email", List.of("rando@supercool.unicorns.com"));
            errorEvent = new String[1];
            errors = new ArrayList<>();
            context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);
            validation = new RegistrationValidation();
            validation.validate(context);

            // Assert the validation result for the second set of values
            assertNull(errorEvent[0]);
            assertEquals(0, errors.size());

            // Test valid IL4 email with custom domains
            valueMap.put("email", List.of("test22@ss.usafa.edu"));
            errorEvent = new String[1];
            errors = new ArrayList<>();
            context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);
            validation = new RegistrationValidation();
            validation.validate(context);

            // Assert the validation result for the third set of values
            assertNull(errorEvent[0]);
            assertEquals(0, errors.size());

            // Test existing X509 registration
            errorEvent = new String[1];
            errors = new ArrayList<>();
            context = ValidationUtils.setupVariables(errorEvent, errors, valueMap);

            // Mock the behavior of X509Tools to return true for isX509Registered
            x509ToolsMock.when(() -> X509Tools.isX509Registered(any(FormContext.class))).thenReturn(true);
            validation = new RegistrationValidation();
            validation.validate(context);

            // Assert the validation result for the fourth set of values
            assertEquals(Errors.INVALID_REGISTRATION, errorEvent[0]);
        }
    }

    @Test
    public void testSuccess() {
    }

    @Test
    public void testBuildPage() {
        try (MockedStatic<X509Tools> x509ToolsMock = mockStatic(X509Tools.class)) {
            String testSubjectDN = "subjectDN";
            String testFirstName = "firstName";
            String testLastName = "lastName";
            String testEmail = "email";

            RegistrationValidation subject = new RegistrationValidation();
            FormContext context = mock(FormContext.class);
            LoginFormsProvider form = mock(LoginFormsProvider.class);

            // Mock the X509Tools to return a non-null value for getCACInfo
            x509ToolsMock.when(() -> X509Tools.getCACInfo(any(FormContext.class)))
                    .thenReturn(new CACInfo(testSubjectDN, testFirstName, testLastName, testEmail));

            subject.buildPage(context, form);

            // Verify that the setAttribute method was called with the expected arguments
            verify(form).setAttribute(Common.FORM_CAC_SUBJECT_DN, testSubjectDN);
            verify(form).setAttribute(Common.FORM_CAC_FIRST_NAME, testFirstName);
            verify(form).setAttribute(Common.FORM_CAC_LAST_NAME, testLastName);
            verify(form).setAttribute(Common.FORM_CAC_EMAIL, testEmail);
        }
    }

    @Test
    public void testGetDisplayType() {
        RegistrationValidation subject = new RegistrationValidation();
        assertEquals("UDS Registration Validation", subject.getDisplayType());
    }

    @Test
    public void testGetId() {
        RegistrationValidation subject = new RegistrationValidation();
        assertEquals("registration-validation-action", subject.getId());
    }

    @Test
    public void testIsConfigurable() {
        RegistrationValidation subject = new RegistrationValidation();
        assertFalse(subject.isConfigurable());
    }

    @Test
    public void testGetRequirementChoices() {
        RegistrationValidation subject = new RegistrationValidation();
        AuthenticationExecutionModel.Requirement[] expected = { AuthenticationExecutionModel.Requirement.REQUIRED };
        assertArrayEquals(expected, subject.getRequirementChoices());
    }
}
