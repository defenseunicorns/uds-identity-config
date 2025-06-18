/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { RegistrationFormData } from "./types";

/**
 * Navigate to the login page and verifying it's exsistence
 */
Cypress.Commands.add("loginPage", () => {
  cy.visit("https://sso.uds.dev");

  // skip the DoD PKI Detected Pop Up
  cy.avoidX509();

  // Verify login page via existence of button
  cy.get('input[name="login"][type="submit"]').should("be.visible");
});

/**
 * Navigate to the registration page, supply form data, and attempt to register user
 */
Cypress.Commands.add("registrationPage", (formData: RegistrationFormData) => {
  cy.loginPage();

  // Verify the presence of the registration link and then click
  cy.contains(".footer-text a", "Register now").should("be.visible").click();

  // Verify client cert has been loaded properly by this header being present
  cy.contains("h3", "DoD PKI User Registration").should("be.visible");

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
 * Verify User is logged into user account portal
 */
Cypress.Commands.add("verifyLoggedIn", () => {
  // Intercept the GET request to verify successful login
  cy.intercept({
    method: 'GET',
    url: 'https://sso.uds.dev/realms/uds/account/?userProfileMetadata=true',
  }).as('getUserProfile');

  // skip the DoD PKI Detected Pop Up
  cy.avoidX509();

  // Wait for the network request to complete with an increased timeout
  cy.wait('@getUserProfile', { timeout: 10000 }).its('response.statusCode').should('eq', 200);
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
  cy.wait(1000);

  cy.document().then((doc) => {
    const cancelButton = doc.querySelector('#kc-cancel');

    if (cancelButton) {
      cy.wrap(cancelButton)
        .scrollIntoView()
        .should('be.visible')
        .click();
    }
  });
})

Cypress.Commands.add("getAccessToken", () => {
  return cy.exec('uds zarf tools kubectl get secret keycloak-client-secrets -n keycloak -o jsonpath="{.data.uds-operator}"').then((result) => {
    expect(result.code).to.eq(0);
    expect(result.stdout).not.contains(" ");

    const clientSecret = Buffer.from(result.stdout, 'base64').toString('utf-8');

    return cy.request({
      method: "POST",
      url: "https://keycloak.admin.uds.dev/realms/uds/protocol/openid-connect/token",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: {
        client_id: "uds-operator",
        client_secret: `${clientSecret}`,
        grant_type: "client_credentials",
      },
    }).then((response) => {
      expect(response.status).to.eq(200);
      const accessToken = response.body.access_token;
      expect(accessToken).to.be.a("string");

      const tokenParts = accessToken.split('.');
      const tokenPayload = Buffer.from(tokenParts[1], 'base64').toString('utf-8');

      expect(tokenPayload).contains("manage-clients");

      return accessToken;
    });
  });
});