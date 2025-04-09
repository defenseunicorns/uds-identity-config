/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { RegistrationFormData } from "../support/types";

describe("CAC Registration Flow", () => {
  const formData: RegistrationFormData = {
    firstName: "John",
    lastName: "Doe",
    organization: "Defense Unicorns",
    username: "john_doe",
    email: "johndoe@defenseunicorns.com",
    password: "CAC",
    affiliation: "Contractor",
    payGrade: "N/A",
  };

  it("Successful CAC Registration", () => {
    cy.registrationPage(formData);

    // Verify Successful Registration and on User Account Page
    cy.verifyLoggedIn();
  });

  it("Successfull Login of CAC Registered User", () => {
    // Navigate to login page
    cy.visit("https://sso.uds.dev");


    // Verify DoD PKI Detected Banner on Login page
    cy.get(".form-group .alert-info").should("be.visible").contains("h2", "DoD PKI Detected");
    cy.get(".form-group #certificate_subjectDN")
      .should("be.visible")
      .contains("C=US,ST=Colorado,L=Colorado Springs,O=Defense Unicorns,CN=uds.dev");

    // Verify that PKI User information is correct
    cy.get(".form-group").contains("label", "You will be logged in as:").should("be.visible");
    cy.get(".form-group #username").should("be.visible").contains("john_doe");

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
    };

    cy.registrationPage(formData);

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
    };

    cy.registrationPage(formData);

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
    };

    cy.registrationPage(formData);

    // password isn't complex enough
    cy.contains(
      "span.message-details",
      "Invalid password: must contain at least 2 special characters.",
    ).should("be.visible");
  });
});
