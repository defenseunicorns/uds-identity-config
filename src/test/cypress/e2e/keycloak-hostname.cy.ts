/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Keycloak Hostname Routing", () => {
  it("uses the public origin for tenant discovery", () => {
    cy.request("https://sso.uds.dev/realms/uds/.well-known/openid-configuration")
      .its("body.issuer")
      .should("eq", "https://sso.uds.dev/realms/uds");
  });

  it("uses the admin origin for admin discovery", () => {
    cy.request("https://keycloak.admin.uds.dev/realms/uds/.well-known/openid-configuration")
      .its("body.issuer")
      .should("eq", "https://keycloak.admin.uds.dev/realms/uds");
  });

  it("keeps the admin console on the admin origin", () => {
    cy.request("https://keycloak.admin.uds.dev/admin/master/console/")
      .its("body")
      .should("contain", '"serverBaseUrl": "https://keycloak.admin.uds.dev"')
      .and("not.contain", '"serverBaseUrl": "https://sso.uds.dev"');
  });

  it("redirects the public admin path to the public account URL", () => {
    cy.request({
      url: "https://sso.uds.dev/admin/",
      followRedirect: false,
    })
      .its("headers.location")
      .should("eq", "https://sso.uds.dev/realms/uds/account");
  });
});
