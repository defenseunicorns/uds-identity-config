/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Keycloak Admin UI client policy configuration", () => {
  const profilesUrl = "https://keycloak.admin.uds.dev/admin/realms/uds/client-policies/profiles";
  const field = "ownership-enforced-clients";
  let originalProfiles: any;

  before(() => {
    cy.getAccessToken("KEYCLOAK_ADMIN").then((token) => {
      cy.request({ url: profilesUrl, headers: { Authorization: `Bearer ${token}` }, log: false })
        .then(({ body }) => { originalProfiles = body; });
    });
  });

  after(() => {
    if (!originalProfiles) return;
    cy.getAccessToken("KEYCLOAK_ADMIN").then((token) => {
      cy.request({
        method: "PUT", url: profilesUrl, headers: { Authorization: `Bearer ${token}` },
        body: originalProfiles, log: false,
      }).its("status").should("eq", 204);
    });
  });

  it("shows executor fields in the Keycloak Admin UI and persists a saved change in the API", () => {
    cy.intercept("GET", "**/client-policies/profiles*").as("loadProfiles");
    cy.visit("https://keycloak.admin.uds.dev/admin/master/console/#/uds/realm-settings/client-policies/UDS%20Client%20Profile/edit-profile/uds-operator-permissions");
    cy.getValueFromSecret("keycloak", "keycloak-admin-password", "username")
      .then((username) => cy.get("#username").type(username, { log: false }));
    cy.getValueFromSecret("keycloak", "keycloak-admin-password", "password")
      .then((password) => cy.get("#password").type(password, { log: false }));
    cy.get("#kc-login").click();
    cy.wait("@loadProfiles").its("response.statusCode").should("eq", 200);
    for (const label of [
      "Ownership-enforced clients",
      "Additional Allowed Protocol Mappers",
      "Use UDS Default Allowed Protocol Mappers",
      "Additional Allowed Client Scopes",
      "Use UDS Default Client Scopes",
    ]) {
      cy.contains("label", label).should("be.visible");
    }
    cy.get(`[data-testid="config.${field}0"]`).clear().type("ownership-ui-test");
    cy.intercept("PUT", "**/client-policies/profiles").as("saveProfiles");
    cy.get('[data-testid="addExecutor-saveBtn"]').click();
    cy.wait("@saveProfiles").its("response.statusCode").should("eq", 204);
    cy.getAccessToken("KEYCLOAK_ADMIN").then((token) => {
      cy.request({ url: profilesUrl, headers: { Authorization: `Bearer ${token}` }, log: false })
        .then(({ body }) => {
          const executor = body.profiles.find((profile: any) => profile.name === "UDS Client Profile")
            .executors.find((entry: any) => entry.executor === "uds-operator-permissions");
          expect(executor.configuration[field][0]).to.eq("ownership-ui-test");
        });
    });
  });
});
