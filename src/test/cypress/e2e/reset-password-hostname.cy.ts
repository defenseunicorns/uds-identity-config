/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Reset Password URL", () => {
  ["https://sso.uds.dev", "https://keycloak.admin.uds.dev"].forEach(loginHost => {
    it(`uses the public SSO hostname when loaded from ${loginHost}`, () => {
      const params = new URLSearchParams({
        client_id: "account",
        redirect_uri: "https://sso.uds.dev/realms/uds/account/",
        response_type: "code",
        scope: "openid",
      });

      cy.visit(`${loginHost}/realms/uds/protocol/openid-connect/auth?${params.toString()}`);
      cy.location("host").should("eq", new URL(loginHost).host);
      cy.get('a[href*="/login-actions/reset-credentials"]')
        .should("have.attr", "href")
        .and("match", /^https:\/\/sso\.uds\.dev\/realms\/uds\/login-actions\/reset-credentials\?/);
    });
  });
});
