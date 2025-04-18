/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("UDS Operator Client Credentials", () => {
    if (Cypress.env("PEPR_KEYCLOAK_CLIENT_STRATEGY") !== "dynamic_client_registration") {
        it ("UDS Operator can obtain Access Token", () => {
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

        it("UDSClientPolicyPermissionsExecutor removes non-whitelisted mappers and claims", () => {
            cy.exec("uds zarf tools kubectl apply -f ./resources/test-package.yaml").its('code').should('eq', 0);
            cy.exec("uds zarf tools kubectl wait --for=condition=Ready package/test-package -n test-package --timeout=300s").its('code').should('eq', 0);

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
                    const testClient = response.body.find((client: any) => client.clientId === "uds-core-admin-test");
                    const protocolMappersUrl = `https://keycloak.admin.uds.dev/admin/realms/uds/clients/${testClient.id}/protocol-mappers/models`;
                    cy.request({
                        method: 'GET',
                        url: protocolMappersUrl,
                        headers: {
                            'Authorization': `Bearer ${accessToken}`,
                            'Content-Type': 'application/json'
                        },
                    }).then((response) => {
                        expect(response.status).to.eq(200);

                        interface ProtocolMapper {
                            name: string;
                        }
                        const protocolMappers = response.body as ProtocolMapper[];
                        expect(protocolMappers).to.have.length(3);
                        expect(protocolMappers.map((mapper) => mapper.name)).to.include.members([
                            "valid-protocol-mapper-1",
                            "valid-protocol-mapper-2",
                            "valid-protocol-mapper-3",
                        ]);
                    });
                });
            });
        });
    }
});
