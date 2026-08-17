/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

interface KeycloakUser {
  id: string;
  username: string;
}

const runId = Date.now().toString(36);
const usernames: string[] = [];

function openRegistrationPage() {
  cy.loginPage();
  cy.contains(".footer-text a", "Create Account").should("be.visible").click();
  cy.get("#password").should("be.visible");
}

function fillRegistrationForm(
  username: string,
  email: string,
  password?: string,
  confirmation?: string,
) {
  cy.get("label").contains("First name").next("input").clear().type("Non");
  cy.get("label").contains("Last name").next("input").clear().type("CAC");
  cy.get("label")
    .contains("Unit, Organization or Company Name")
    .next("input")
    .clear()
    .type("Defense Unicorns");
  cy.get("label").contains("Username").next("input").clear().type(username);
  cy.get("label").contains("Email").next("input").clear().type(email);

  const passwordField = cy.get("label").contains("Password").next("input");
  const confirmationField = cy.get("label").contains("Confirm password").next("input");

  if (password !== undefined) {
    passwordField.clear().type(password);
  }
  if (confirmation !== undefined) {
    confirmationField.clear().type(confirmation);
  }

  cy.get("#affiliation").should("be.visible").select("Contractor");
  cy.get("#rank").should("be.visible").select("N/A");
  cy.window().then(window => {
    for (let event = 0; event <= 250; event++) {
      window.dispatchEvent(new MouseEvent("mousemove"));
    }
  });
}

function findUser(username: string) {
  return cy.getAccessToken("KEYCLOAK_ADMIN").then(accessToken => {
    return cy.request<KeycloakUser[]>({
      method: "GET",
      url: "https://keycloak.admin.uds.dev/admin/realms/uds/users",
      qs: { username, exact: true },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
  });
}

function assertUserCount(username: string, expected: number) {
  findUser(username).then(response => {
    expect(response.body).to.have.length(expected);
  });
}

function submitRegistration() {
  cy.intercept("POST", "**/realms/uds/login-actions/registration*").as("registrationSubmit");
  cy.get("#do-register").should("not.be.disabled").click();
  cy.wait("@registrationSubmit");
}

describe("Non-CAC Registration Flow", () => {
  after(() => {
    usernames.forEach(username => cy.deleteUserByUsername(username));
  });

  it("rejects a missing password on the server", () => {
    const username = `non_cac_missing_${runId}`;
    usernames.push(username);

    openRegistrationPage();
    fillRegistrationForm(username, `${username}@uds.dev`);
    cy.get("label").contains("Password").next("input").invoke("removeAttr", "required").clear();
    cy.get("label")
      .contains("Confirm password")
      .next("input")
      .invoke("removeAttr", "required")
      .clear();
    submitRegistration();

    cy.contains("span.message-details", "Please specify password.").should("be.visible");
    assertUserCount(username, 0);
  });

  it("creates a user with a valid password", () => {
    const username = `non_cac_valid_${runId}`;
    usernames.push(username);

    openRegistrationPage();
    fillRegistrationForm(username, `${username}@uds.dev`, "PrettyUnicorns1!!", "PrettyUnicorns1!!");
    submitRegistration();

    assertUserCount(username, 1);
  });
});
