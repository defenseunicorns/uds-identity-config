/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

const adminUrl = "https://keycloak.admin.uds.dev/admin/realms/uds";
const requiredAction = "webauthn-register-passwordless";
const username = `webauthn_${Date.now().toString(36)}`;
const password = "Testingpassword1!!";

// Use a real browser WebAuthn response, including authenticatorAttachment, without hardware.
function webAuthnCommand(command: string, params = {}) {
  return Cypress.automation("remote:debugger:protocol", { command, params });
}

describe("WebAuthn passwordless registration", () => {
  let accessToken: string;
  let userId: string;
  let originalAction: Record<string, unknown>;

  function adminRequest(method: "GET" | "POST" | "PUT" | "DELETE", path: string, body?: unknown) {
    return cy.then(() =>
      cy.request({
        method,
        url: `${adminUrl}/${path}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body,
      }),
    );
  }

  before(() => {
    cy.getAccessToken("KEYCLOAK_ADMIN").then(token => {
      accessToken = token;
    });
    adminRequest("GET", `authentication/required-actions/${requiredAction}`).then(response => {
      originalAction = response.body;
      return adminRequest("PUT", `authentication/required-actions/${requiredAction}`, {
        ...originalAction,
        enabled: true,
      });
    });
    adminRequest("POST", "users", {
      username,
      firstName: "WebAuthn",
      lastName: "Test",
      email: `${username}@uds.dev`,
      emailVerified: true,
      enabled: true,
      requiredActions: [requiredAction],
      credentials: [{ type: "password", value: password, temporary: false }],
    }).then(response => {
      userId = response.headers.location.toString().split("/").pop()!;
    });
  });

  after(() => {
    if (userId) adminRequest("DELETE", `users/${userId}`);
    if (originalAction) {
      adminRequest("PUT", `authentication/required-actions/${requiredAction}`, originalAction);
    }
  });

  afterEach(() => {
    cy.then(() => webAuthnCommand("WebAuthn.disable"));
  });

  it("stores a passwordless credential when the authenticator returns its attachment", () => {
    cy.then(() => webAuthnCommand("WebAuthn.enable"));
    cy.then(() =>
      webAuthnCommand("WebAuthn.addVirtualAuthenticator", {
        options: {
          protocol: "ctap2",
          transport: "usb",
          hasResidentKey: true,
          hasUserVerification: true,
          isUserVerified: true,
          automaticPresenceSimulation: true,
        },
      }),
    );

    cy.loginPage();
    cy.loginUser(username, password);
    cy.get("#registerWebAuthn").should("be.visible");
    cy.window().then(window => {
      cy.stub(window, "prompt").returns("Passwordless regression test");
    });
    cy.intercept("POST", "**/realms/uds/login-actions/required-action*").as("registration");
    cy.get("#registerWebAuthn").click();
    cy.wait("@registration");

    adminRequest("GET", `users/${userId}/credentials`).then(response => {
      const credentials = response.body.filter(
        (credential: { type: string }) => credential.type === "webauthn-passwordless",
      );
      expect(credentials).to.have.length(1);
    });
  });
});
