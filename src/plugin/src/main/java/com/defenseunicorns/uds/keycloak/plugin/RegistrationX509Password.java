/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationPage;
import org.keycloak.authentication.forms.RegistrationPassword;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RegistrationX509Password extends RegistrationPassword {
    private static final Logger LOG = Logger.getLogger(RegistrationX509Password.class);
    private static final String DIGITS_POLICY = "digits";
    private static final String LENGTH_POLICY = "length";
    private static final String LOWER_CASE_POLICY = "lowerCase";
    private static final String NOT_EMAIL_POLICY = "notEmail";
    private static final String NOT_USERNAME_POLICY = "notUsername";
    private static final String SPECIAL_CHARS_POLICY = "specialChars";
    private static final String UPPER_CASE_POLICY = "upperCase";


    /**
     * Provider ID.
     */
    public static final String PROVIDER_ID = "registration-x509-password-action";
    /**
     * Requirement choices.
     */
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED};

    /**
     * Custom implementation.
     */
    @Override
    public String getHelpText() {
        return "Disables password registration if CAC authentication is possible.";
    }

    /**
     * Custom implementation.
     */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return new ArrayList<>();
    }

    /**
     * Custom implementation.
     */
    @Override
    public void validate(final ValidationContext context) {
        String x509Username = X509Tools.getX509Username(context);
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        String password = formData.getFirst(RegistrationPage.FIELD_PASSWORD);
        String passwordConfirm = formData.getFirst(RegistrationPage.FIELD_PASSWORD_CONFIRM);

        if ((password == null || password.isEmpty()) && (passwordConfirm == null || passwordConfirm.isEmpty())) {
            if (x509Username != null && isExplicitX509Registration(formData)) {
                LOG.info("Allowing explicit X509 registration without password credential");
                context.success();
                return;
            }
        }

        List<FormMessage> passwordPolicyErrors = validateConfiguredPasswordPolicy(context, formData, password);
        if (!passwordPolicyErrors.isEmpty()) {
            LOG.info("Rejecting registration password due to configured realm password policy");
            context.error(Errors.INVALID_REGISTRATION);
            formData.remove(RegistrationPage.FIELD_PASSWORD);
            formData.remove(RegistrationPage.FIELD_PASSWORD_CONFIRM);
            context.validationError(formData, passwordPolicyErrors);
            return;
        }

        super.validate(context);
    }

    private boolean isExplicitX509Registration(final MultivaluedMap<String, String> formData) {
        String cacSubjectDN = formData.getFirst(Common.FORM_CAC_SUBJECT_DN);
        return cacSubjectDN != null && !cacSubjectDN.isBlank();
    }

    private List<FormMessage> validateConfiguredPasswordPolicy(
            final ValidationContext context,
            final MultivaluedMap<String, String> formData,
            final String password) {

        List<FormMessage> errors = new ArrayList<>();
        if (password == null || password.isEmpty() || context.getRealm().getPasswordPolicy() == null) {
            return errors;
        }

        PasswordPolicy policy = context.getRealm().getPasswordPolicy();

        Integer minLength = getIntPolicyConfig(policy, LENGTH_POLICY);
        if (minLength != null && password.length() < minLength) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, "invalidPasswordMinLengthMessage", minLength));
        }

        Integer minDigits = getIntPolicyConfig(policy, DIGITS_POLICY);
        if (minDigits != null && countMatches(password, Character::isDigit) < minDigits) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, "invalidPasswordMinDigitsMessage", minDigits));
        }

        Integer minLowerCase = getIntPolicyConfig(policy, LOWER_CASE_POLICY);
        if (minLowerCase != null && countMatches(password, Character::isLowerCase) < minLowerCase) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, "invalidPasswordMinLowerCaseCharsMessage", minLowerCase));
        }

        Integer minUpperCase = getIntPolicyConfig(policy, UPPER_CASE_POLICY);
        if (minUpperCase != null && countMatches(password, Character::isUpperCase) < minUpperCase) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, "invalidPasswordMinUpperCaseCharsMessage", minUpperCase));
        }

        Integer minSpecialChars = getIntPolicyConfig(policy, SPECIAL_CHARS_POLICY);
        if (minSpecialChars != null && countMatches(password, c -> !Character.isLetterOrDigit(c)) < minSpecialChars) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, "invalidPasswordMinSpecialCharsMessage", minSpecialChars));
        }

        String username = formData.getFirst(RegistrationPage.FIELD_USERNAME);
        if (hasPolicy(policy, NOT_USERNAME_POLICY) && username != null && password.equals(username)) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, "invalidPasswordNotUsernameMessage"));
        }

        String email = formData.getFirst(RegistrationPage.FIELD_EMAIL);
        if (hasPolicy(policy, NOT_EMAIL_POLICY) && email != null && password.equals(email)) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, "invalidPasswordNotEmailMessage"));
        }

        return errors;
    }

    private Integer getIntPolicyConfig(final PasswordPolicy policy, final String key) {
        Object value = policy.getPolicyConfig(key);
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                LOG.warnf("Ignoring non-integer password policy config for %s: %s", key, stringValue);
            }
        }
        return null;
    }

    private boolean hasPolicy(final PasswordPolicy policy, final String key) {
        Set<String> policies = policy.getPolicies();
        return policies != null && policies.contains(key);
    }

    private int countMatches(final String value, final CharacterMatcher matcher) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (matcher.matches(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    @FunctionalInterface
    private interface CharacterMatcher {
        boolean matches(char value);
    }

    /**
     * Custom implementation.
     */
    @Override
    public void success(final FormContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        UserModel user = context.getUser();

        String password = formData.getFirst(RegistrationPage.FIELD_PASSWORD);
        if ((X509Tools.getX509Username(context) == null)
                || (password != null && !password.isEmpty())) {
            super.success(context);
            // TOTP also enforced in RegistrationValidation class for non-CAC registration
            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
        }
    }

    /**
     * Custom implementation.
     */
    @Override
    public void buildPage(final FormContext context, final LoginFormsProvider form) {
        if (X509Tools.getX509Username(context) == null) {
            form.setAttribute("passwordRequired", true);
        }
    }

    /**
     * Custom implementation.
     */
    @Override
    public boolean requiresUser() {
        return false;
    }

    /**
     * Custom implementation.
     */
    @Override
    public boolean configuredFor(final KeycloakSession session, final RealmModel realm, final UserModel user) {
        return true;
    }

    /**
     * Custom implementation.
     */
    @Override
    public void setRequiredActions(final KeycloakSession session, final RealmModel realm, final UserModel user) {
        // no implementation needed
    }

    /**
     * Custom implementation.
     */
    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    /**
     * Custom implementation.
     */
    @Override
    public void close() {
        // no implementation needed
    }

    /**
     * Custom implementation.
     */
    @Override
    public String getDisplayType() {
        return "UDS X509 Password Validation";
    }

    /**
     * Custom implementation.
     */
    @Override
    public String getReferenceCategory() {
        return PasswordCredentialModel.TYPE;
    }

    /**
     * Custom implementation.
     */
    @Override
    public boolean isConfigurable() {
        return false;
    }

    /**
     * Custom implementation.
     */
    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    /**
     * Custom implementation.
     */
    @Override
    public FormAction create(final KeycloakSession session) {
        return this;
    }

    /**
     * Custom implementation.
     */
    @Override
    public void init(final Config.Scope config) {
        // no implementation needed
    }

    /**
     * Custom implementation.
     */
    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        // no implementation needed
    }

    /**
     * Custom implementation.
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

}
