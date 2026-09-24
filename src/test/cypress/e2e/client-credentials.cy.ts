/**
 * Copyright 2024-2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("UDS Operator Client Credentials", () => {
    it("limits service-account clients to explicitly scoped roles", () => {
        const clients = [
            { clientId: "uds-operator", scopeRole: "manage-clients" },
            { clientId: "uds-opentofu-client", scopeRole: "realm-admin" },
            { clientId: "uds-fleet-admin", scopeRole: "manage-clients" },
        ];

        cy.getAccessToken().then((accessToken) => {
            return cy.request({
                method: "GET",
                url: "https://keycloak.admin.uds.dev/admin/realms/uds/clients",
                qs: { clientId: "realm-management" },
                headers: { Authorization: `Bearer ${accessToken}` },
            }).then((managementResponse) => {
                const [realmManagement] = managementResponse.body;
                expect(realmManagement.clientId).to.eq("realm-management");

                return cy.wrap(clients).each(({ clientId, scopeRole }) => {
                    return cy.request({
                        method: "GET",
                        url: "https://keycloak.admin.uds.dev/admin/realms/uds/clients",
                        qs: { clientId },
                        headers: { Authorization: `Bearer ${accessToken}` },
                    }).then((scopeResponse) => {
                        expect(scopeResponse.status).to.eq(200);
                        const [client] = scopeResponse.body;
                        expect(client.clientId).to.eq(clientId);
                        expect(client.fullScopeAllowed).to.be.false;
                        expect(client.standardFlowEnabled).to.be.false;
                        expect(client.implicitFlowEnabled).to.be.false;
                        expect(client.directAccessGrantsEnabled).to.be.false;
                        expect(client.serviceAccountsEnabled).to.be.true;
                        expect(client.redirectUris).to.deep.equal([]);

                        return cy.request({
                            method: "GET",
                            url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients/${client.id}/scope-mappings/clients/${realmManagement.id}`,
                            headers: { Authorization: `Bearer ${accessToken}` },
                        }).then((mappingResponse) => {
                            expect(mappingResponse.body.map((role: { name: string }) => role.name)).to.deep.equal([scopeRole]);
                        });
                    });
                });
            });
        });

        cy.getClientSecret("uds-opentofu-client").then(({ clientSecret }) => {
            cy.request({
                log: false,
                method: "POST",
                url: "https://keycloak.admin.uds.dev/realms/uds/protocol/openid-connect/token",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                form: true,
                body: {
                    client_id: "uds-opentofu-client",
                    client_secret: clientSecret,
                    grant_type: "client_credentials",
                },
            }).then((response) => {
                expect(response.status).to.eq(200);
                const token = response.body.access_token.split(".")[1];
                const base64 = token.replace(/-/g, "+").replace(/_/g, "/");
                const payload = JSON.parse(atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=")));
                expect(payload.resource_access?.["realm-management"]?.roles || []).to.include("realm-admin");
            });
        });
    });

    it("UDS Operator can obtain Access Token", () => {
        cy.getAccessToken()
    });

    it("UDS Operator can delete its own Client", () => {
        const randomClientId = `client-${Math.random().toString(36).substring(2, 15)}`;

        cy.getAccessToken().then((accessToken: string) => {
            cy.request({
                method: 'POST',
                url: 'https://keycloak.admin.uds.dev/admin/realms/uds/clients',
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json'
                },
                body: {
                    clientId: randomClientId,
                    enabled: true,
                    publicClient: true,
                    redirectUris: ['http://localhost/*']
                }
            }).then((response) => {
                expect(response.status).to.eq(201);
                return response.headers['location'] as string;
            }).then((clientUrl: string) => {
                cy.request({
                    method: 'DELETE',
                    url: clientUrl,
                    headers: {
                        'Authorization': `Bearer ${accessToken}`,
                        'Content-Type': 'application/json'
                    },
                }).then((response) => {
                    expect(response.status).to.eq(204);
                });
            });
        });
    });

    it("UDS Operator can't delete a built-in Client", () => {
        cy.getAccessToken().then((accessToken: string) => {
            cy.request({
                method: 'GET',
                url: 'https://keycloak.admin.uds.dev/admin/realms/uds/clients',
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json'
                },
            }).then((response) => {
                expect(response.status).to.eq(200);
                const brokerClient = response.body.find((client: any) => client.clientId === "broker");
                const clientUrl = `https://keycloak.admin.uds.dev/admin/realms/uds/clients/${brokerClient.id}`;
                cy.request({
                    failOnStatusCode: false,
                    method: 'DELETE',
                    url: clientUrl,
                    headers: {
                        'Authorization': `Bearer ${accessToken}`,
                        'Content-Type': 'application/json'
                    },
                }).then((response) => {
                    expect(response.status).to.eq(400);
                    expect(response.body).to.deep.equal({
                        error: "unauthorized_client",
                        error_description: "The Client doesn't have the created-by=uds-operator attribute. Rejecting request."
                    });
                });
            });
        });
    });

    it("UDSClientPolicyPermissionsExecutor validates mappers and claims", () => {
        cy.task("exec", "uds zarf tools kubectl apply -f ./resources/test-package-not-passing-validation.yaml").its('exitCode').should('eq', 0);
        cy.task("exec", "uds zarf tools kubectl wait --for=condition=Ready=false package/test-package-not-passing-validation -n test-package-not-passing-validation --timeout=300s").its('exitCode').should('eq', 0);
        cy.task("exec", "kubectl get events -n test-package-not-passing-validation")
          .its('stdout')
          .should('include', '{"error":"invalid_client","error_description":"The Protocol Mapper non-whitelisted-protocol-mapper is not allowed. Rejecting request."}');

      cy.getAccessToken().then((accessToken: string) => {
            cy.request({
                method: 'GET',
                url: 'https://keycloak.admin.uds.dev/admin/realms/uds/clients',
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json'
                },
            }).then((response) => {
                expect(response.status).to.eq(200);
                const testClient = response.body.find((client: any) => client.clientId === "test-package-not-passing-validation");
                // Ensure that this Client hasn't been created
                expect(testClient).to.be.undefined;
            });
        });
    });

    it("UDSClientPolicyPermissionsExecutor supports adding additional Protocol Mappers declaratively", () => {
      cy.task("exec", "uds zarf tools kubectl apply -f ./resources/test-package-allowed-protocol-mapper.yaml").its('exitCode').should('eq', 0);
      cy.task("exec", "uds zarf tools kubectl wait --for=condition=Ready=true package/test-package-allowed-protocol-mapper -n test-package-allowed-protocol-mapper --timeout=300s").its('exitCode').should('eq', 0);
    });

    it("Dynamic Client Registration is disabled", () => {
        cy.request({
            method: "POST",
            url: "https://keycloak.admin.uds.dev/realms/uds/clients-registrations/default",
            failOnStatusCode: false,
            headers: {
                "Content-Type": "application/json",
            },
            body: {
                clientId: "dcr-should-be-disabled",
                redirectUris: ["http://localhost"],
                publicClient: true,
            },
        }).then((response) => {
            expect([401, 403]).to.include(response.status);
        });
    });
});
