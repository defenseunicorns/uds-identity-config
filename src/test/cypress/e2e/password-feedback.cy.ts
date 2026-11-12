/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

function openLoginPage() {
  // A baseUrl allows this UI spec to run against a standalone Keycloak preview.
  if (Cypress.config("baseUrl")) {
    cy.visit(Cypress.config("baseUrl")!);
  } else {
    cy.loginPage();
  }
}

describe("Password feedback", () => {
  beforeEach(() => {
    openLoginPage();
    cy.contains("a", "Create Account").click();
    cy.get("#password-requirements").should("be.visible");
  });

  it("hides completed requirements and restores them when they are no longer met", () => {
    cy.get("#password, #password-confirm").each(input => {
      cy.wrap(input).should("have.attr", "type", "password");
    });
    cy.get("[data-password-toggle]").should("not.exist");
    cy.get("[data-password-rule]").each(rule => {
      cy.wrap(rule).should("be.visible");
    });
    cy.get("#password").should("have.attr", "aria-describedby", "password-requirements");
    cy.get("#password").type("a");
    cy.get('[data-password-rule="lowerCase"]').should("not.be.visible");
    cy.get('[data-password-rule="length"]').should("be.visible");

    cy.get("#password").clear().type("PrettyUnicorns1!!");
    cy.get("[data-password-rule]").each(rule => {
      cy.wrap(rule).should("not.be.visible");
    });
    cy.get("[data-password-pending]").should("not.be.visible");
    cy.get("[data-password-progress]").should("contain.text", "6 of 6 password requirements met.");

    cy.get("#password").type("{backspace}{backspace}");
    cy.get('[data-password-rule="specialChars"]').should("be.visible");
    cy.get('[data-password-rule="lowerCase"]').should("not.be.visible");

    cy.get("#password").clear();
    cy.get("[data-password-rule]").each(rule => {
      cy.wrap(rule).should("be.visible");
    });
    cy.get("[data-password-progress]").should("have.text", "");
  });

  it("compares passwords exactly and waits until blur before showing a mismatch", () => {
    cy.get("#password").type("PrettyUnicorns1!!");
    cy.get("#password-confirm").type("PrettyUnicorns1!! ");
    cy.get("#password-match").should("not.be.visible");
    cy.get("#password-confirm").blur();
    cy.get("#password-match").should("be.visible").and("have.text", "Passwords do not match.");

    cy.get("#password-confirm").clear().type("PrettyUnicorns1!!");
    cy.get("#password-match").should("have.text", "Passwords match.");
    cy.get("#password").type("x");
    cy.get("#password-match").should("have.text", "Passwords do not match.");
    cy.get("#password-confirm").clear();
    cy.get("#password-match").should("not.be.visible");
  });

  it("supports Unicode character classes and counts spaces as special characters like Keycloak", () => {
    cy.get("#password").invoke("val", "Éé١  abcdefghijkl").trigger("input");
    cy.get("[data-password-rule]").each(rule => {
      cy.wrap(rule).should("not.be.visible");
    });
  });

  it("rechecks notUsername when either field changes, ignoring case", () => {
    cy.get("#username").type("PrettyUnicorns1!!");
    cy.get("#password").type("prettyunicorns1!!");
    cy.get('[data-password-rule="notUsername"]').should("be.visible");
    cy.get("#username").type("x");
    cy.get('[data-password-rule="notUsername"]').should("not.be.visible");
    cy.get("#password").type("x");
    cy.get('[data-password-rule="notUsername"]').should("be.visible");
  });
});

describe("Update-password feedback", () => {
  it("shows live requirements and confirmation on the update-password form", () => {
    // Request the action without changing the shared test user's password or required actions.
    cy.intercept({ method: "GET", url: "**/protocol/openid-connect/auth*", times: 1 }, request => {
      request.query.kc_action = "UPDATE_PASSWORD";
    });
    openLoginPage();
    cy.loginUser("testing_user", "Testingpassword1!!");

    cy.get("#password-requirements").should("be.visible");
    cy.get("#password-new").should("have.attr", "type", "password").type("TESTING_USER");
    cy.get('[data-password-rule="notUsername"]').should("be.visible");
    cy.get("#password-new").clear().type("PrettyUnicorns1!!");
    cy.get("[data-password-pending]").should("not.be.visible");
    cy.get("#password-confirm")
      .should("have.attr", "type", "password")
      .type("DifferentPassword1!!")
      .blur();
    cy.get("#password-match").should("be.visible").and("have.text", "Passwords do not match.");
    cy.get("#password-confirm").clear().type("PrettyUnicorns1!!");
    cy.get("#password-match").should("have.text", "Passwords match.");
  });
});
