/**
 * Copyright 2024-2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("UDS Operator Client Credentials", () => {
    let foreignClientUrl: string | undefined;
    let cleanupOwnershipPackage = false;

    afterEach(() => {
        if (cleanupOwnershipPackage) {
            cy.task("exec", "uds zarf tools kubectl delete -f ./resources/test-package-allowed-protocol-mapper.yaml --ignore-not-found")
                .its("exitCode").should("eq", 0);
            cy.then(() => { cleanupOwnershipPackage = false; });
        }
        if (foreignClientUrl) {
            cy.getAccessToken("KEYCLOAK_ADMIN").then((token) => {
                cy.request({
                    method: "DELETE", url: foreignClientUrl!,
                    headers: { Authorization: `Bearer ${token}` }, log: false,
                }).its("status").should("eq", 204);
            });
            cy.then(() => { foreignClientUrl = undefined; });
        }
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

    it("UDS Operator cannot create a client with a disallowed Protocol Mapper", () => {
        cy.task("exec", "uds zarf tools kubectl apply -f ./resources/test-package-not-passing-validation.yaml").its('exitCode').should('eq', 0);
        cy.task("exec", "uds zarf tools kubectl wait --for=condition=Ready=false package/test-package-not-passing-validation -n test-package-not-passing-validation --timeout=300s").its('exitCode').should('eq', 0);
        cy.task("exec", "kubectl get events -n test-package-not-passing-validation")
          .its('stdout')
          .should('include', 'Error: 403, Forbidden, {"error":"HTTP 403 Forbidden"}');

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
        const clientId = "test-package-allowed-protocol-mapper";
        const waitForPackage = (generation: number) => {
            cy.task("exec", `uds zarf tools kubectl wait --for=jsonpath='{.status.observedGeneration}'=${generation} package/${clientId} -n ${clientId} --timeout=300s`).its("exitCode").should("eq", 0);
            cy.task("exec", `uds zarf tools kubectl wait --for=condition=Ready=true package/${clientId} -n ${clientId} --timeout=300s`).its("exitCode").should("eq", 0);
        };
        const assertMapper = (present: boolean) => {
            cy.getAccessToken("UDS_OPERATOR").then((token) => {
                cy.request({
                    url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${clientId}`,
                    headers: { Authorization: `Bearer ${token}` },
                    log: false,
                }).then((response) => {
                    const keycloakClient = response.body[0];
                    return cy.request({
                        url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients/${keycloakClient.id}/protocol-mappers/models`,
                        headers: { Authorization: `Bearer ${token}` },
                        log: false,
                    }).then((mapperResponse) => {
                        expect(mapperResponse.body.some((mapper: { name: string }) => mapper.name === "hardcoded-sub-claim")).to.eq(present);
                    });
                });
            });
        };

        cy.getAccessToken("KEYCLOAK_ADMIN").then((token) => {
            cy.request({
                url: "https://keycloak.admin.uds.dev/admin/realms/uds",
                headers: { Authorization: `Bearer ${token}` },
                log: false,
            }).then(({ body }) => {
                for (const attribute of [
                    "allowedProtocolMapperTypesForClientAdministrators",
                    "uds.protocolMapperDelegation.addedTypes",
                ]) {
                    expect(body.attributes[attribute], attribute).to.be.a("string");
                    expect(body.attributes[attribute].split(","), attribute).to.include("oidc-hardcoded-claim-mapper");
                }
            });
        });

        cy.task("exec", "uds zarf tools kubectl apply -f ./resources/test-package-allowed-protocol-mapper.yaml").its("exitCode").should("eq", 0);
        cy.task("exec", `uds zarf tools kubectl wait --for=condition=Ready=true package/${clientId} -n ${clientId} --timeout=300s`).its("exitCode").should("eq", 0);
        assertMapper(true);

        cy.task("exec", `uds zarf tools kubectl patch package ${clientId} -n ${clientId} --type=json -p='[{"op":"replace","path":"/spec/sso/0/protocolMappers","value":[]}]' -o json`)
            .then((result) => {
                waitForPackage(JSON.parse(result.stdout).metadata.generation);
                assertMapper(false);
            });

        cy.task("exec", `uds zarf tools kubectl patch package ${clientId} -n ${clientId} --type=json -p='[{"op":"add","path":"/spec/sso/0/protocolMappers/-","value":{"name":"hardcoded-sub-claim","protocol":"openid-connect","protocolMapper":"oidc-hardcoded-claim-mapper","config":{"claim.name":"groups","claim.value":"/UDS Core/Auditor","jsonType.label":"String","access.token.claim":"true"}}}]' -o json`)
            .then((result) => {
                waitForPackage(JSON.parse(result.stdout).metadata.generation);
                assertMapper(true);
            });

    });

    it("UDS Operator reconciles its owned client and synchronizes its real secret", () => {
        const clientId = "test-package-allowed-protocol-mapper";
        cleanupOwnershipPackage = true;
        cy.task("exec", "uds zarf tools kubectl apply -f ./resources/test-package-allowed-protocol-mapper.yaml")
            .its("exitCode").should("eq", 0);
        cy.task("exec", `uds zarf tools kubectl wait --for=condition=Ready=true package/${clientId} -n ${clientId} --timeout=300s`)
            .its("exitCode").should("eq", 0);
        cy.task("exec", `uds zarf tools kubectl patch package ${clientId} -n ${clientId} --type=json -p='[{"op":"replace","path":"/spec/sso/0/name","value":"Secret sync ${Date.now()}"}]' -o json`)
            .then((result) => {
                expect(result.exitCode).to.eq(0);
                const generation = JSON.parse(result.stdout).metadata.generation;
                cy.task("exec", `uds zarf tools kubectl wait --for=jsonpath='{.status.observedGeneration}'=${generation} package/${clientId} -n ${clientId} --timeout=300s`)
                    .its("exitCode").should("eq", 0);
                cy.task("exec", `uds zarf tools kubectl wait --for=condition=Ready=true package/${clientId} -n ${clientId} --timeout=300s`)
                    .its("exitCode").should("eq", 0);
            });
        cy.getAccessToken("UDS_OPERATOR").then((token) => {
            cy.request({
                url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${clientId}`,
                headers: { Authorization: `Bearer ${token}` }, log: false,
            }).then(({ body }) => {
                expect(body).to.have.length(1);
                const client = body[0];
                expect(client.attributes["created-by"]).to.eq("uds-operator");
                expect(client.secret).to.be.a("string").and.not.be.empty;
                expect(client.secret).not.to.eq("**********");
                cy.getValueFromSecret(clientId, `sso-client-${clientId}`, "secret").should("eq", client.secret);
            });
        });
    });

    it("UDS Operator cannot read another client's secret or client details", () => {
        const clientId = `foreign-secret-${Date.now()}`;
        cy.getAccessToken("KEYCLOAK_ADMIN").then((adminToken) => {
            const headers = { Authorization: `Bearer ${adminToken}` };
            cy.request({
                method: "POST", url: "https://keycloak.admin.uds.dev/admin/realms/uds/clients",
                headers, body: { clientId, enabled: true, publicClient: false }, log: false,
            }).then((created) => {
                foreignClientUrl = created.headers.location as string;
                cy.request({ url: foreignClientUrl, headers, log: false }).then(({ body }) => {
                    expect(body.secret).to.be.a("string").and.not.be.empty;
                    expect(body.secret).not.to.eq("**********");
                });
                cy.getAccessToken("UDS_OPERATOR").then((token) => {
                    const operatorHeaders = { Authorization: `Bearer ${token}` };
                    cy.request({
                        url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${clientId}`,
                        headers: operatorHeaders, log: false,
                    }).then(({ body }) => {
                        expect(body).to.have.length(1);
                        expect(body[0].secret).to.eq("**********");
                    });
                    cy.request({
                        url: foreignClientUrl, headers: operatorHeaders,
                        failOnStatusCode: false, log: false,
                    }).then((response) => {
                        expect(response.status).to.eq(400);
                        expect(response.body.error).to.eq("unauthorized_client");
                    });
                });
            });
        });
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
