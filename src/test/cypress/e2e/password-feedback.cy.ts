/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Password feedback", () => {
  beforeEach(() => {
    // A baseUrl allows this UI spec to run against a standalone Keycloak preview.
    if (Cypress.config("baseUrl")) {
      cy.visit(Cypress.config("baseUrl")!);
    } else {
      cy.loginPage();
    }
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
    cy.get("[data-password-progress]").should("contain.text", "5 of 5 character requirements met.");

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
});
