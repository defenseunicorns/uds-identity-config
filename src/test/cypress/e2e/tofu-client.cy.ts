/**
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Tofu Client Management", () => {
    const testClientId = "example-client";
    const testRedirectUri = "https://example.com/*";
    const tfDir = "./test-tf";
    const tofuClientName = "uds-opentofu-client";
    const serviceAccountName = "uds-opentofu-client";
    const serviceAccountNamespace = "keycloak";

    let keycloakAccessToken: string | null = null;

    before(() => {
        // Create a dedicated Kubernetes service account for OpenTofu
        cy.exec(`uds zarf tools kubectl create serviceaccount ${serviceAccountName} -n ${serviceAccountNamespace} --dry-run=client -o yaml | uds zarf tools kubectl apply -f -`);

        // Get a signed JWT from the service account and exchange it for a Keycloak access token
        // via the federated-jwt flow. The Keycloak provider uses jwt_token which sends client_id
        // alongside the assertion, but the federated-jwt authenticator requires sub == client_id.
        // To work around this, we exchange the SA token for a Keycloak access token first.
        return cy.getServiceAccountToken(serviceAccountNamespace, serviceAccountName).then((saToken) => {
            return cy.request({
                method: 'POST',
                url: 'https://keycloak.admin.uds.dev/realms/uds/protocol/openid-connect/token',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                form: true,
                body: {
                    grant_type: 'client_credentials',
                    client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
                    client_assertion: saToken,
                },
                failOnStatusCode: false,
            }).then((response) => {
                expect(response.status).to.eq(200, 'Signed JWT authentication should succeed');
                keycloakAccessToken = response.body.access_token;
                expect(keycloakAccessToken).to.be.a('string').and.not.be.empty;

                // Create test OpenTofu configuration
                const tfConfig = `
              terraform {
                required_providers {
                  keycloak = {
                    source  = "keycloak/keycloak"
                    version = "5.7.0"
                  }
                }
                required_version = ">= 1.0.0"
              }

              provider "keycloak" {
                client_id     = "${tofuClientName}"
                access_token  = var.keycloak_access_token
                url           = "https://keycloak.admin.uds.dev"
                realm         = "uds"
              }

              resource "keycloak_openid_client" "test_client" {
                realm_id  = "uds"
                client_id = "${testClientId}"
                name      = "${testClientId}"

                access_type                  = "PUBLIC"
                enabled                      = true
                standard_flow_enabled        = true
                direct_access_grants_enabled = true
                service_accounts_enabled     = false

                valid_redirect_uris = [
                  "${testRedirectUri}"
                ]
              }

              variable "keycloak_access_token" {
                type        = string
                description = "Keycloak access token obtained via signed JWT authentication"
                sensitive   = true
              }
            `;

                // Create directory and write the single OpenTofu file
                cy.exec(`mkdir -p ${tfDir}`);
                cy.writeFile(`${tfDir}/main.tf`, tfConfig);
            });
        });
    });

    after(() => {
        if (!keycloakAccessToken) return;

        // Run OpenTofu destroy
        cy.exec(`cd ${tfDir} && tofu destroy -auto-approve -var="keycloak_access_token=${keycloakAccessToken}"`, {
            failOnNonZeroExit: false
        }).then((result) => {
            expect(result.exitCode).to.eq(0, "OpenTofu destroy should succeed");
        });

        // Verify client is deleted
        cy.getAccessToken('KEYCLOAK_ADMIN').then((adminToken) => {
            cy.request({
                method: 'GET',
                url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${testClientId}`,
                headers: {
                    'Authorization': `Bearer ${adminToken}`,
                    'Content-Type': 'application/json'
                },
                failOnStatusCode: false
            }).then((response) => {
                const client = response.body.find((c: any) => c.clientId === testClientId);
                expect(client).to.be.undefined;
            });
        });

        // Clean up test directory
        cy.exec(`rm -rf ${tfDir}`);
    });

    it("should apply OpenTofu configuration", () => {
        if (!keycloakAccessToken) throw new Error('Access token not initialized');

        // Initialize OpenTofu
        cy.exec(`cd ${tfDir} && tofu init`, {
            failOnNonZeroExit: false
        }).then(() => {
            // Apply OpenTofu with the Keycloak access token
            return cy.exec(
                `cd ${tfDir} && tofu apply -auto-approve ` +
                `-var="keycloak_access_token=${keycloakAccessToken}" ` +
                '-no-color -input=false -json',
                { failOnNonZeroExit: false }
            ).then((applyResult) => {
                if (applyResult.exitCode !== 0) {
                    let errorDetails = applyResult.stderr;
                    try {
                        const jsonOutput = JSON.parse(applyResult.stdout);
                        errorDetails = JSON.stringify(jsonOutput, null, 2);
                    } catch (e) {
                        errorDetails = applyResult.stderr || applyResult.stdout;
                    }

                    throw new Error(`OpenTofu apply failed with code ${applyResult.exitCode}.\n` +
                                  `ERROR DETAILS:\n${errorDetails}`);
                }

                expect(applyResult.exitCode).to.eq(0, "OpenTofu apply should succeed");
            });
        });
    });

    it("should verify client creation in Keycloak", () => {
        cy.getAccessToken('KEYCLOAK_ADMIN').then((accessToken) => {
            cy.request({
                method: 'GET',
                url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${testClientId}`,
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json'
                }
            }).then((response) => {
                expect(response.status).to.eq(200);
                const client = response.body.find((c: any) => c.clientId === testClientId);
                expect(client).to.exist;

                expect(client.enabled).to.be.true;
                expect(client.standardFlowEnabled).to.be.true;
                expect(client.directAccessGrantsEnabled).to.be.true;
                expect(client.redirectUris).to.include(testRedirectUri);
            });
        });
    });

    it("should fail with invalid JWT token", () => {
        const invalidToken = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature';
        const tempDir = "./test-temp-invalid-jwt";

        cy.exec(`mkdir -p ${tempDir}`);
        cy.exec(`cp ${tfDir}/main.tf ${tempDir}/`);

        return cy.exec(`cd ${tempDir} && tofu init`, {
            failOnNonZeroExit: false
        }).then(() => {
            return cy.exec(
                `cd ${tempDir} && tofu apply -auto-approve -var="keycloak_access_token=${invalidToken}"`,
                { failOnNonZeroExit: false }
            );
        }).then((result) => {
            cy.log('OpenTofu apply result:', result);
            expect(result.exitCode).not.to.eq(0);
            expect(result.stderr).to.include('401');
            return cy.exec(`rm -rf ${tempDir}`);
        });
    });

    it("should fail with unauthorized client ID", () => {
        const unauthorizedConfig = `
            terraform {
                required_providers {
                    keycloak = {
                        source  = "keycloak/keycloak"
                        version = "5.7.0"
                    }
                }
            }

            provider "keycloak" {
                client_id     = "unauthorized-client"
                access_token  = "invalid-token"
                url           = "https://keycloak.admin.uds.dev"
                realm         = "uds"
            }

            resource "keycloak_openid_client" "test" {
                realm_id    = "uds"
                client_id   = "test-unauthorized-client"
                name        = "test-unauthorized-client"
                access_type = "PUBLIC"
                enabled     = true
            }
        `;

        const tempDir = "./test-tf-unauthorized";
        cy.exec(`mkdir -p ${tempDir}`);
        cy.writeFile(`${tempDir}/main.tf`, unauthorizedConfig);

        return cy.exec(`cd ${tempDir} && tofu init`, { failOnNonZeroExit: false })
            .then(() => {
                return cy.exec(
                    `cd ${tempDir} && tofu apply -auto-approve`,
                    { failOnNonZeroExit: false }
                );
            })
            .then((result) => {
                cy.log('OpenTofu apply result:', result);
                expect(result.exitCode).not.to.eq(0);
                const errorOutput = result.stderr + result.stdout;
                expect(errorOutput).to.match(/401 Unauthorized|403 Forbidden/);
                return cy.exec(`rm -rf ${tempDir}`);
            });
    });
});
