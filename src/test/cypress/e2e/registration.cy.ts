/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { RegistrationFormData } from "../support/types";

// Below mechanism is purely a convenience option that helps to rerun this test multiple times without
// having to clean up things manually.
let __anyTestFailed = false;
afterEach(function () {
  if (this.currentTest && this.currentTest.state === 'failed') {
    __anyTestFailed = true;
  }
});

after(() => {
  if (__anyTestFailed) {
    cy.log('Skipping cleanup in after() because a test failed');
    return;
  }
  Cypress.on('fail', () => false)
  cy.deleteUserByUsername('doug.unicorn');
  Cypress.on('fail', () => true)
});

describe("CAC Registration Flow", () => {
  const formData: RegistrationFormData = {
    firstName: "Doug",
    lastName: "Unicorn",
    organization: "Defense Unicorns",
    username: "doug.unicorn",
    email: "doug.unicorn@uds.dev",
    password: "CAC",
    affiliation: "Contractor",
    payGrade: "N/A",
    cac_c: "C=US",
    cac_o: "O=U.S. Government",
    cac_cn: "CN=UNICORN.DOUG.ROCKSTAR.1234567890",
  };

  it("Successful CAC Registration", () => {
    cy.registrationPage(formData, true);

    // Verify Successful Registration and on User Account Page
    cy.verifyLoggedIn();
  });

  it("Successful Login of CAC Registered User", () => {
    // Navigate to login page
    cy.visit("https://sso.uds.dev");


    // Verify DoD PKI Detected Banner on Login page
    cy.get(".form-group .alert-info").should("be.visible").contains("h3", "DoD PKI Detected");
    cy.get(".form-group #certificate_subjectDN")
      .should("be.visible")
      // FIPS and non-FIPS mode use different formats for the subject DN. That's why we check if all parts are present instead of
      // a full string match.
      .contains("C=US").contains("O=U.S. Government").contains("CN=UNICORN.DOUG.ROCKSTAR.1234567890")


    // Verify that PKI User information is correct
    cy.get(".form-group").contains("label", "You will be logged in as:").should("be.visible");
    cy.get(".form-group #username").should("be.visible").contains(formData.username);

    // Sign in using the PKI
    cy.get("#kc-login").should("be.visible").click();

    // Verify Successful Registration and on User Account Page
    cy.verifyLoggedIn();
  });
});

describe("Registration Tests", () => {
  it("Duplicate Registration", () => {
    const formData: RegistrationFormData = {
      firstName: "Doug",
      lastName: "Doug",
      organization: "Defense Unicorns",
      username: "testing_user",
      email: "testing_user@uds.dev",
      password: "PrettyUnicorns1!!",
      affiliation: "Contractor",
      payGrade: "N/A",
      cac_c: "C=US",
      cac_o: "O=U.S. Government",
      cac_cn: "CN=UNICORN.DOUG.ROCKSTAR.1234567890",
    };

    cy.registrationPage(formData, false);

    // duplicate user trying to register with PKI should result in this warning
    cy.contains("span.message-details", "Email already exists.").should("be.visible");
    cy.contains("span.message-details", "Username already exists.").should("be.visible");
  });

  it("Password Length", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "Pretty1!!",
      affiliation: "Contractor",
      payGrade: "N/A",
      cac_c: "C=US",
      cac_o: "O=U.S. Government",
      cac_cn: "CN=UNICORN.DOUG.ROCKSTAR.1234567890",
    };

    cy.registrationPage(formData, false);

    // password isn't long enough
    cy.contains("span.message-details", "Invalid password: minimum length 15.").should(
      "be.visible",
    );
  });

  it("Password Complexity", () => {
    const formData: RegistrationFormData = {
      firstName: "New",
      lastName: "User",
      organization: "Defense Unicorns",
      username: "new_user",
      email: "newuser@gmail.com",
      password: "PrettyUnicorns1",
      affiliation: "Contractor",
      payGrade: "N/A",
      cac_c: "C=US",
      cac_o: "O=U.S. Government",
      cac_cn: "CN=UNICORN.DOUG.ROCKSTAR.1234567890",
    };

    cy.registrationPage(formData, false);

    // password isn't complex enough
    cy.contains(
      "span.message-details",
      "Invalid password: must contain at least 2 special characters.",
    ).should("be.visible");
  });
});
