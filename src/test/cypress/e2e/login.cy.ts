/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { RegistrationFormData } from "../support/types";

describe("Login Flow", () => {
  it("Existing User", () => {
    // existing user
    const formData: RegistrationFormData = {
      firstName: "Testing",
      lastName: "User",
      username: "testing_user",
      password: "Testingpassword1!!",
    };

    cy.loginPage();
    cy.loginUser(formData.username, formData.password);

    // Verify Successful Registration and on User Account Page
    cy.verifyLoggedIn();
  });

  it("Invalid User Creds", () => {
    const formData: RegistrationFormData = {
      username: "testing_user",
      password: "PrettyUnicorns1!!",
    };

    cy.loginPage();
    cy.loginUser(formData.username, formData.password);

    // user doesn't exist or password is incorrect
    cy.contains("p", "Invalid username or password.").should("be.visible");
  });

  it("REALM_SSO_SESSION_MAX_IN_FLIGHT_LOGINS_PER_USER configuration test", () => {
    cy.getValueFromSecret("keycloak", "keycloak-realm-env", "REALM_SSO_SESSION_MAX_IN_FLIGHT_LOGINS_PER_USER").then(decoded => {
      const secretLimit = parseInt((decoded || "").trim(), 10);
      expect(Number.isFinite(secretLimit), `Decoded secret value should be a number, got: '${decoded}'`).to.be.true;

      cy.exec("uds zarf tools kubectl get statefulset -n keycloak keycloak -o jsonpath='{.spec.template.spec.containers[0].args}'").then(stsResult => {
        const argsOutput = stsResult.stdout || "";
        const match = /--spi-authentication-sessions--map--auth-sessions-limit(?:=|\s+)?(\d+)/.exec(argsOutput);
        expect(match, `Could not find '--spi-authentication-sessions--map--auth-sessions-limit' in args: ${argsOutput}`).to.exist;

        const argsLimit = parseInt(match?.[1] || "NaN", 10);
        expect(Number.isFinite(argsLimit), `Args limit should be a number, got: '${match?.[1]}'`).to.be.true;

        expect(argsLimit, "StatefulSet args limit should match Secret value").to.equal(secretLimit);
      });
    });
  });

  it("REALM_SSO_SESSION_MAX_PER_USER configuration test", () => {
    cy.getValueFromSecret("keycloak", "keycloak-realm-env", "REALM_SSO_SESSION_MAX_PER_USER").then(decoded => {
      const maxSessions = parseInt((decoded || "").trim(), 10);
      expect(Number.isFinite(maxSessions), `Decoded secret value should be a number, got: '${decoded}'`).to.be.true;

      const attempts = maxSessions + 1;
      const formData: RegistrationFormData = {
        firstName: "Testing",
        lastName: "User",
        username: "testing_sessions",
        password: "Testingpassword1!!",
      };

      Cypress._.times(attempts, (i: number) => {
        cy.log(`Login attempt ${i + 1} of ${attempts}`);

        // Clear cookies and local storage between runs
        cy.clearCookies();
        cy.clearLocalStorage();

        cy.loginPage();
        cy.loginUser(formData.username, formData.password);

        // On the last attempt, expect an error page with an error
        if (i === attempts - 1) {
          cy.contains("There are too many sessions").should("be.visible");
        }
      });
    });
  });
});
