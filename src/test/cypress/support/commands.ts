import { RegistrationFormData } from "./types";
import { BrowserMultiFormatReader } from '@zxing/browser';
import { authenticator } from 'otplib';

/**
 * Navigate to the login page and verifying it's exsistence
 */
Cypress.Commands.add("loginPage", () => {
  cy.visit("https://sso.uds.dev");

  // Verify Access to Account Management landing page
  cy.contains("h1", "UDS Identity - My Account", { timeout: 12000 }).should("be.visible");

  // click on sign in button
  cy.get("#landingSignInButton").should("be.visible").click();

  // Verify login page via existence of button
  cy.get('input[name="login"][type="submit"]').should("be.visible");
});

/**
 * Supply the login page user creds and attempt to login
 */
Cypress.Commands.add("loginUser", (username: string, password: string) => {
  // fill in user creds
  cy.get("#username").should("be.visible").type(username);
  cy.get("#password").should("be.visible").type(password);

  // click login button
  cy.get("#kc-login").should("be.visible").click();
});

/**
 * Navigate to the registration page, supply form data, and attempt to register user
 */
Cypress.Commands.add("registrationPage", () => {
    // go to registration page
    cy.loginPage();

    // Verify the presence of the registration link and then click
    cy.contains(".footer-text a", "Click here").should("be.visible").click();
});

/**
 * Register new User
 */
Cypress.Commands.add("registerUser", (formData: RegistrationFormData) => {
  // Fill Registration form inputs
  cy.get("label").contains("First name").next("input").type(formData.firstName);
  cy.get("label").contains("Last name").next("input").type(formData.lastName);
  cy.get("label")
    .contains("Unit, Organization or Company Name")
    .next("input")
    .type(formData.organization);
  cy.get("label").contains("Username").next("input").type(formData.username);
  cy.get("label").contains("Email").next("input").type(formData.email);

  // only use password fields if not using CAC registration
  if (formData.password != "CAC") {
    cy.get("label").contains("Password").next("input").type(formData.password);
    cy.get("label").contains("Confirm password").next("input").type(formData.password);
  }

  // Fill Registration form Drop-downs
  cy.get("#affiliation").should("be.visible").select(formData.affiliation);
  cy.get("#rank").should("be.visible").select(formData.payGrade);

  // bypass human confidence check by filling the access request notes textarea
  cy.get("body")
    .should("be.visible")
    .type(
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec vitae nunc nec est mattis faucibus ac at justo. Nullam auctor tellus nec sapien tristique, eget feugiat dolor accumsan. Duis sit amet aliquet sapien. Suspendisse non felis et ante posuere dapibus.",
    );

  // register user
  cy.get("#do-register").should("be.visible").click();
});

/**
 * Navigate to grafana URL and verify redirected to sso.uds.dev
 */
Cypress.Commands.add("accessGrafana", () => {

  cy.visit('https://grafana.admin.uds.dev');
  // Assert that the URL is redirected to the SSO URL
  cy.url().should('include', 'https://sso.uds.dev');

  cy.avoidX509();

  // Verify login page via existence of button
  cy.get('input[name="login"][type="submit"]').should("be.visible");

});

/**
 * Avoid the x509 pop up when necessary ( ex not testing with x509 cert )
 */
Cypress.Commands.add("avoidX509", () => {
  // Check if the cancel button is present and click it if it is
  if(Cypress.env('X509_CERT')){
    cy.get('body').then($body => {
      if ($body.find('input#kc-cancel').length > 0) {
        cy.get('input#kc-cancel').click();
      }
    });
  }
});

/**
 * Setup OTP after registration
 */
Cypress.Commands.add("setupOTP", (username) => {
  // Verify on OTP page
  cy.get('#alert-error').should('be.visible').and('contain', 'You need to set up multi-factor authentication (MFA) to activate your account.');

  // Capture QR code image URL
  cy.get('img#kc-totp-secret-qr-code').then($img => {
    const img = $img[0] as HTMLImageElement;
    const image = new Image();
    image.width = img.width;
    image.height = img.height;
    image.src = img.src;
    image.crossOrigin = 'Anonymous';
    return image;
  })
  .then(image => {
    const reader = new BrowserMultiFormatReader();
    return reader.decodeFromImageElement(image[0])
  })
  .then(result => {
    // Extract the secret from the text object
    const text = result.getText();
    const secretMatch = text.match(/secret=([A-Z2-7]+=*)/);
    if (secretMatch) {
      const secret = secretMatch[1];

      // Set environment variable and generate OTP
      Cypress.env(`${username}_OTP_SECRET`, secret);
      const otp = authenticator.generate(secret);

      // Enter the OTP in the form and submit
      cy.get('#totp').type(otp);
      cy.get('#saveTOTPBtn').click();
    } else {
      cy.log('Secret not found in the text.');
    }
  });
});

/**
 * Enter Users OTP
 */
Cypress.Commands.add("enterOTP", (username) => {
  // Retrieve users OTP secret
  const otpSecret = Cypress.env(`${username}_OTP_SECRET`);

  // Generate OTP
  const otp = authenticator.generate(otpSecret);

  // Enter OTP
  cy.get('input#otp').type(otp);
  cy.get('input#kc-login').click();
});

/**
 * Verify on User Account Page
 */
Cypress.Commands.add("verifyUserAccountPage", (formData) => {
  cy.get("#landingLoggedInUser")
  .should("be.visible")
  .and("contain", formData.firstName + " " + formData.lastName);
});
