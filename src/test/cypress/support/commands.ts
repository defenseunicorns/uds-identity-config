import { RegistrationFormData } from "./types";

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
 * Navigate to the registration page, supply form data, and attempt to register user
 */
Cypress.Commands.add("registrationPage", (formData: RegistrationFormData) => {
  cy.loginPage();

  // if the dod pki login page is present then we need to click a different button to get to the registration page
  cy.contains("h2", "DoD PKI Detected").then($header => {
    if ($header.length > 0 && $header.text().trim() === "DoD PKI Detected") {
      // If the header is present, click the "Ignore" button to get to the registration page
      cy.get("#kc-cancel").should("be.visible").click();
    }
  });

  // Verify the presence of the registration link and then click
  cy.contains(".footer-text a", "Click here").should("be.visible").click();

  // Verify client cert has been loaded properly by this header being present
  cy.contains("h2", "DoD PKI User Registration").should("be.visible");

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
  cy.get("#user\\.attributes\\.affiliation").should("be.visible").select(formData.affiliation);
  cy.get("#user\\.attributes\\.rank").should("be.visible").select(formData.payGrade);

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
 * Supply the login page user creds and attempt to login
 */
Cypress.Commands.add("loginUser", (username: string, password: string) => {
  cy.loginPage();

  // fill in user creds
  cy.get("#username").should("be.visible").type(username);
  cy.get("#password").should("be.visible").type(password);

  // click login button
  cy.get("#kc-login").should("be.visible").click();
});
