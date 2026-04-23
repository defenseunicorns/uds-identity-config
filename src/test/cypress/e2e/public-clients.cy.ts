/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("UDS Operator Public Clients", () => {
    const manifest = "./resources/test-package-public-client.yaml";
    const clientId = "test-package-public-client";
    const namespace = "test-package-public-client";

    after(() => {
        cy.exec(`uds zarf tools kubectl delete -f ${manifest} --ignore-not-found`, {
            failOnNonZeroExit: false,
        });
    });

    it("admits a UDS Package with a PKCE public client and creates it in Keycloak", () => {
        cy.exec(`uds zarf tools kubectl apply -f ${manifest}`).its("exitCode").should("eq", 0);
        cy.exec(
            `uds zarf tools kubectl wait --for=condition=Ready=true package/${clientId} -n ${namespace} --timeout=300s`,
        ).its("exitCode").should("eq", 0);

        cy.getAccessToken().then((accessToken: string) => {
            cy.request({
                method: "GET",
                url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${clientId}`,
                headers: {
                    Authorization: `Bearer ${accessToken}`,
                    "Content-Type": "application/json",
                },
            }).then((response) => {
                expect(response.status).to.eq(200);
                const client = response.body.find((c: any) => c.clientId === clientId);
                expect(client, "public client should exist in Keycloak").to.exist;
                expect(client.publicClient).to.be.true;
                expect(client.standardFlowEnabled).to.be.true;
                expect(client.attributes["pkce.code.challenge.method"]).to.eq("S256");
            });
        });
    });
});
