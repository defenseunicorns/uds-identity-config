package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationUserCreation;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import java.util.ArrayList;
import java.util.List;

public class RegistrationValidation extends RegistrationUserCreation {

    public static final String PROVIDER_ID = "registration-validation-action";

    /**
     * Requirement choices.
     */
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED };

    private static void bindRequiredActions(final UserModel user, final String x509Username) {
        // Default actions for all users
        user.addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);

        if (x509Username == null) {
            // This user must configure MFA for their login
            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
        }
    }

    private static void processX509UserAttribute(final RealmModel realm, final UserModel user,
            final String x509Username) {
        if (x509Username != null) {
            // Bind the X509 attribute to the user
            user.setSingleAttribute(Common.USER_ID_ATTRIBUTE, x509Username);
        }
    }

    @Override
    public void success(final FormContext context) {
        UserModel user = context.getUser();
        RealmModel realm = context.getRealm();
        String x509Username = X509Tools.getX509Username(context);

        processX509UserAttribute(realm, user, x509Username);
        bindRequiredActions(user, x509Username);
    }

    @Override
    public void buildPage(final FormContext context, final LoginFormsProvider form) {
        String subjectDN = X509Tools.getX509SubjectDN(context);
        if (subjectDN != null) {
            form.setAttribute("cacSubjectDN", subjectDN);
        }
    }

    @Override
    public String getDisplayType() {
        return "UDS Registration Validation";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    /**
     * Validate the registration form.
     *
     * @param context The validation context
     */
    @Override
    public void validate(final ValidationContext context) {
        // Get the form data
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        // Create a list to hold any errors
        List<FormMessage> errors = new ArrayList<>();

        String eventError = Errors.INVALID_REGISTRATION;

        // Check if a X509 was used to authenticate and if it's already registered
        if (X509Tools.getX509Username(context) != null && X509Tools.isX509Registered(context)) {
            // X509 auth, invite code not required
            errors.add(new FormMessage(null, "Sorry, this CAC seems to already be registered."));
            context.error(Errors.INVALID_REGISTRATION);
            context.validationError(formData, errors);
        }

        if (!errors.isEmpty()) {
            context.error(eventError);
            context.validationError(formData, errors);
        } else {
            context.success();
        }

    }
}
