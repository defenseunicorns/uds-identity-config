/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Client Credentials Grant", () => {
    it("should obtain an access token using client credentials", () => {
        cy.exec('uds zarf tools kubectl get secret keycloak-client-secrets -n keycloak -o jsonpath="{.data.uds-operator}"').then((result) => {
            expect(result.code).to.eq(0)
            // A space might indicate some weird error. Let's ensure the Client Secret at least looks good
            expect(result.stdout).not.contains(" ")

            const clientSecret = Buffer.from(result.stdout, 'base64').toString('utf-8');

            cy.request({
                method: "POST",
                url: "https://keycloak.admin.uds.dev/realms/uds/protocol/openid-connect/token",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                },
                body: {
                    client_id: "uds-operator",
                    client_secret: `${clientSecret}`,
                    grant_type: "client_credentials",
                },
            }).then((response) => {
                expect(response.status).to.eq(200);
                const accessToken = response.body.access_token;
                expect(accessToken).to.be.a("string");

                const tokenParts = accessToken.split('.');
                const tokenPayload = Buffer.from(tokenParts[1], 'base64').toString('utf-8');

                expect(tokenPayload).contains("manage-clients");
            });

        });
    });
});
