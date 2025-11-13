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
Cypress.Commands.add("registrationPage", (formData: RegistrationFormData, expectNewUser: boolean) => {
  cy.loginPage();

  // The CAC registration has two variants - if a new user tries to register, it shows a link to the
  // registration in the alerts panel. If it some other case - in the footer
  if (expectNewUser) {
    cy.contains("a", "Create account with CAC").should("be.visible").click();
  } else {
    cy.contains(".footer-text a", "Create Account").should("be.visible").click();
  }

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
 * Supply the login page user creds (specifically email) and attempt to login
 */
Cypress.Commands.add("loginUserWithEmail", (email: string, password: string) => {
  // fill in user creds
  cy.get("#username").should("be.visible").type(email);
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

/**
 * Gets the client secret for a specified client from Keycloak
 * @param clientId The client ID to get the secret for
 * @returns {Promise<{accessToken: string, clientSecret: string}>} An object containing the access token and client secret
 */
Cypress.Commands.add("getClientSecret", (clientId: string) => {
  return cy.getAccessToken().then((accessToken) => {
    return cy.request({
      method: 'GET',
      url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${encodeURIComponent(clientId)}`,
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }).then((response) => {
      const client = response.body.find((c: any) => c.clientId === clientId);
      if (!client) {
        throw new Error(`Client with ID '${clientId}' not found`);
      }
      return cy.request({
        method: 'GET',
        url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients/${client.id}/client-secret`,
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json'
        }
      }).then((secretResponse) => {
        return {
          accessToken,
          clientSecret: secretResponse.body.value
        };
      });
    });
  });
});

Cypress.Commands.add("getAccessToken", () => {
  return cy.exec('uds zarf tools kubectl get secret keycloak-client-secrets -n keycloak -o jsonpath="{.data.uds-operator}"').then((result) => {
    expect(result.exitCode).to.eq(0);
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

/**
 * Retrieve and base64-decode a value from a Kubernetes Secret by key.
 * The "key" parameter is required and must be non-empty; an error is thrown otherwise.
 */
Cypress.Commands.add("getValueFromSecret", (namespace: string, secretName: string, key: string) => {
  if (!key || key.trim().length === 0) {
    throw new Error('getValueFromSecret: "key" is required and cannot be empty');
  }
  const cmd = `uds zarf tools kubectl get secret -n ${namespace} ${secretName} -o json`;
  return cy.exec(cmd).then((result) => {
    expect(result.exitCode, `Failed to fetch Secret '${secretName}' in namespace '${namespace}'`).to.eq(0);

    let parsed: any = {};
    try {
      parsed = JSON.parse(result.stdout || "{}");
    } catch (e) {
      throw new Error(`Unable to parse Secret JSON for '${secretName}' in '${namespace}': ${(e as Error).message}`);
    }

    const data = (parsed && parsed.data) || {};
    const b64Value = data[key];
    expect(b64Value, `Secret '${secretName}' in ns '${namespace}' must contain key '${key}'`).to.exist;
    const decoded = Buffer.from((b64Value || "").trim(), "base64").toString("utf-8");
    return decoded;
  });
});