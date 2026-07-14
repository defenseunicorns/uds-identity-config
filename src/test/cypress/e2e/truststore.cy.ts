/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Keycloak Truststore", () => {
  it("does not keep the legacy PKCS12 truststore", () => {
    const legacyTruststore = "/opt/keycloak/data/keycloak-truststore.p12";

    cy.exec(
      `uds zarf tools kubectl exec keycloak-0 -n keycloak -- sh -c 'if [ -e ${legacyTruststore} ]; then echo "${legacyTruststore} exists"; exit 1; fi'`,
    )
      .its("exitCode")
      .should("eq", 0);
  });
});
