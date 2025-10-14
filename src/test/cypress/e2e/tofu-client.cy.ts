/**
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

describe("Tofu Client Management", () => {
    const testClientId = "example-client";
    const testRedirectUri = "https://example.com/*";
    const tfDir = "./test-tf";
    const tofuClientName = "uds-opentofu-client";

    let credentials: { accessToken: string; clientSecret: string } | null = null;

    before(() => {
        // Get credentials once
        return cy.getClientSecret(tofuClientName).then((creds) => {
            credentials = creds;

            // Create test OpenTofu configuration
            const tfConfig = `
              terraform {
                required_providers {
                  keycloak = {
                    source  = "keycloak/keycloak"
                    version = "5.5.0"
                  }
                }
                required_version = ">= 1.0.0"
              }

              provider "keycloak" {
                client_id     = "${tofuClientName}"
                client_secret = var.keycloak_client_secret
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

              variable "keycloak_client_secret" {
                type        = string
                description = "Client secret for the Keycloak provider"
                sensitive   = true
              }
            `;

            // Create directory and write the single OpenTofu file
            cy.exec(`mkdir -p ${tfDir}`);
            cy.writeFile(`${tfDir}/main.tf`, tfConfig);
        });
    });

    after(() => {
        if (!credentials) return;

        const { accessToken, clientSecret } = credentials;

        // Run OpenTofu destroy with the client secret
        cy.exec(`cd ${tfDir} && tofu destroy -auto-approve -var="keycloak_client_secret=${clientSecret}"`, {
            failOnNonZeroExit: false
        }).then((result) => {
            expect(result.exitCode).to.eq(0, "OpenTofu destroy should succeed");
        });

        // Verify client is deleted
        cy.request({
            method: 'GET',
            url: `https://keycloak.admin.uds.dev/admin/realms/uds/clients?clientId=${testClientId}`,
            headers: {
                'Authorization': `Bearer ${accessToken}`,
                'Content-Type': 'application/json'
            },
            failOnStatusCode: false
        }).then((response) => {
            const client = response.body.find((c: any) => c.clientId === testClientId);
            expect(client).to.be.undefined;
        });

        // Clean up test directory
        cy.exec(`rm -rf ${tfDir}`);
    });

    it("should apply OpenTofu configuration", () => {
        if (!credentials) throw new Error('Credentials not initialized');

        const { clientSecret, accessToken } = credentials;

        // First, verify the access token is valid
        cy.request({
            method: 'GET',
            url: 'https://keycloak.admin.uds.dev/admin/realms/uds/clients',
            headers: {
                'Authorization': `Bearer ${accessToken}`,
                'Content-Type': 'application/json'
            },
            failOnStatusCode: false
        }).then((authCheck) => {
            if (authCheck.status === 401) {
                throw new Error('Access token is invalid or expired. Please refresh your credentials.');
            }
            expect(authCheck.status).to.eq(200, 'Keycloak API should be accessible with current credentials');
        });

        // Initialize OpenTofu
        cy.exec(`cd ${tfDir} && tofu init`, {
            failOnNonZeroExit: false
        }).then(() => {
            // Apply OpenTofu with detailed logging
            return cy.exec(
                `cd ${tfDir} && tofu apply -auto-approve ` +
                `-var="keycloak_client_secret=${clientSecret}" ` +
                '-no-color -input=false -json',
                { failOnNonZeroExit: false }
            ).then((applyResult) => {
                if (applyResult.exitCode !== 0) {
                    // Try to parse JSON output if available
                    let errorDetails = applyResult.stderr;
                    try {
                        const jsonOutput = JSON.parse(applyResult.stdout);
                        errorDetails = JSON.stringify(jsonOutput, null, 2);
                    } catch (e) {
                        // If JSON parsing fails, use the original error
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
        if (!credentials) throw new Error('Credentials not initialized');

        const { accessToken } = credentials;

        // Get client details
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

            // Verify client configuration
            expect(client.enabled).to.be.true;
            expect(client.standardFlowEnabled).to.be.true;
            expect(client.directAccessGrantsEnabled).to.be.true;
            expect(client.redirectUris).to.include(testRedirectUri);
        });
    });

    it("should fail with invalid client secret", () => {
        const invalidSecret = 'invalid-secret-123';
        const tempDir = "./test-temp-invalid-secret";

        // Create a clean directory for this test
        cy.exec(`mkdir -p ${tempDir}`);

        // Copy the main.tf file to the temp directory
        cy.exec(`cp ${tfDir}/main.tf ${tempDir}/`);

        // Initialize OpenTofu
        return cy.exec(`cd ${tempDir} && tofu init`, {
            failOnNonZeroExit: false
        }).then(() => {
            // Apply OpenTofu with invalid secret
            return cy.exec(
                `cd ${tempDir} && tofu apply -auto-approve -var="keycloak_client_secret=${invalidSecret}"`,
                { failOnNonZeroExit: false }
            );
        }).then((result) => {
            // Log the error for debugging
            cy.log('OpenTofu apply result:', result);

            // Should fail with non-zero exit code
            expect(result.exitCode).not.to.eq(0);

            // Check for specific error message
            expect(result.stderr).to.include('401');

            // Clean up
            return cy.exec(`rm -rf ${tempDir}`);
        });
    });

    it("should fail with unauthorized client ID", () => {
        const unauthorizedConfig = `
            terraform {
                required_providers {
                    keycloak = {
                        source  = "keycloak/keycloak"
                        version = "5.5.0"
                    }
                }
            }

            provider "keycloak" {
                client_id     = "unauthorized-client"
                client_secret = "invalid-secret"  # Using hardcoded invalid secret
                url           = "https://keycloak.admin.uds.dev"
                realm         = "uds"
            }

            # Add a minimal resource to validate the configuration
            resource "keycloak_openid_client" "test" {
                realm_id    = "uds"
                client_id   = "test-unauthorized-client"
                name        = "test-unauthorized-client"
                access_type = "PUBLIC"
                enabled     = true
            }
        `;

        // Write the invalid config to a temporary file
        const tempDir = "./test-tf-unauthorized";
        cy.exec(`mkdir -p ${tempDir}`);
        cy.writeFile(`${tempDir}/main.tf`, unauthorizedConfig);

        // Initialize OpenTofu
        return cy.exec(`cd ${tempDir} && tofu init`, { failOnNonZeroExit: false })
            .then(() => {
                // Try to apply with invalid client
                return cy.exec(
                    `cd ${tempDir} && tofu apply -auto-approve`,
                    { failOnNonZeroExit: false }
                );
            })
            .then((result) => {
                // Log the full error for debugging
                cy.log('OpenTofu apply result:', result);

                // Should fail with non-zero exit code
                expect(result.exitCode).not.to.eq(0);

                // Check for unauthorized error (401 or 403)
                const errorOutput = result.stderr + result.stdout;
                expect(errorOutput).to.match(/401 Unauthorized|403 Forbidden/);

                // Clean up
                return cy.exec(`rm -rf ${tempDir}`);
            });
    });
});