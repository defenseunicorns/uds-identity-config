/**
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

// Behavioral tests for the scoped `uds-fleet-admin` client (federated JWT auth).
// Requires the test bundle to set realmInitEnv.FLEET_CLIENT_ENABLED=true and the
// resources/fleet-admin-test-setup.yaml prerequisites (namespace + service account).

const CLIENTS_URL = "https://keycloak.admin.uds.dev/admin/realms/uds/clients";
const SETUP_MANIFEST = "./resources/fleet-admin-test-setup.yaml";

describe("Fleet Admin Client (federated JWT)", () => {
  let fixtureClientUrl: string | undefined;
  let nonOwnedClientUrl: string | undefined;

  afterEach(() => {
    if (!fixtureClientUrl) return;
    cy.getFleetAdminAccessToken().then((accessToken: string) => {
      cy.request({
        method: "DELETE",
        url: fixtureClientUrl!,
        headers: { Authorization: `Bearer ${accessToken}` },
        failOnStatusCode: false,
        log: false,
      }).its("status").should("be.oneOf", [204, 404]);
    });
    // Clear after queued cleanup so retries cannot reuse an earlier fixture.
    cy.then(() => { fixtureClientUrl = undefined; });
  });

  afterEach(() => {
    if (!nonOwnedClientUrl) return;
    cy.getAccessToken("UDS_OPERATOR").then((accessToken) => {
      cy.request({
        method: "DELETE", url: nonOwnedClientUrl!,
        headers: { Authorization: `Bearer ${accessToken}` },
        failOnStatusCode: false, log: false,
      }).its("status").should("be.oneOf", [204, 404]);
    });
    cy.then(() => { nonOwnedClientUrl = undefined; });
  });

  before(() => {
    cy.task("exec", `uds zarf tools kubectl apply -f ${SETUP_MANIFEST}`)
      .its("exitCode")
      .should("eq", 0);
  });

  after(() => {
    cy.task("exec", `uds zarf tools kubectl delete -f ${SETUP_MANIFEST} --ignore-not-found`)
      .its("exitCode")
      .should("eq", 0);
  });

  it("uds-fleet-admin can obtain an Access Token via its service-account token", () => {
    cy.getFleetAdminAccessToken();
  });

  it("uds-fleet-admin can create and delete its own fleet-* Client", () => {
    const fleetClientId = `fleet-agent-${Math.random().toString(36).substring(2, 15)}`;

    cy.getFleetAdminAccessToken().then((accessToken: string) => {
      cy.request({
        method: "POST",
        url: CLIENTS_URL,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: {
          clientId: fleetClientId,
          enabled: true,
          publicClient: true,
          redirectUris: ["http://localhost/*"],
        },
      })
        .then((response) => {
          expect(response.status).to.eq(201);
          return response.headers["location"] as string;
        })
        .then((clientUrl: string) => {
          // Confirm the client exists and the plugin stamped ownership to uds-fleet-admin.
          cy.request({
            method: "GET",
            url: clientUrl,
            headers: {
              Authorization: `Bearer ${accessToken}`,
              "Content-Type": "application/json",
            },
          }).then((getResponse) => {
            expect(getResponse.status).to.eq(200);
            expect(getResponse.body.clientId).to.eq(fleetClientId);
            expect(getResponse.body.attributes["created-by"]).to.eq("uds-fleet-admin");
          });

          // uds-fleet-admin can delete a client it created (ownership check passes).
          cy.request({
            method: "DELETE",
            url: clientUrl,
            headers: {
              Authorization: `Bearer ${accessToken}`,
              "Content-Type": "application/json",
            },
          }).then((response) => {
            expect(response.status).to.eq(204);
          });
        });
    });
  });

  it("uds-fleet-admin can't create a Client without the fleet- prefix", () => {
    cy.getFleetAdminAccessToken().then((accessToken: string) => {
      cy.request({
        failOnStatusCode: false,
        method: "POST",
        url: CLIENTS_URL,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: {
          clientId: `not-fleet-${Math.random().toString(36).substring(2, 15)}`,
          enabled: true,
          publicClient: true,
          redirectUris: ["http://localhost/*"],
        },
      }).then((response) => {
        expect(response.status).to.eq(400);
        expect(response.body).to.deep.equal({
          error: "invalid_client",
          error_description: 'The Client ID must start with "fleet-". Rejecting request.',
        });
      });
    });
  });

  it("uds-fleet-admin can't rename its own Client outside the fleet- prefix", () => {
    const fleetClientId = `fleet-agent-${Math.random().toString(36).substring(2, 15)}`;
    const renamedClientId = `not-fleet-${Math.random().toString(36).substring(2, 15)}`;

    cy.getFleetAdminAccessToken().then((accessToken: string) => {
      cy.request({
        method: "POST",
        url: CLIENTS_URL,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: {
          clientId: fleetClientId,
          enabled: true,
          publicClient: true,
          redirectUris: ["http://localhost/*"],
        },
      })
        .then((response) => {
          expect(response.status).to.eq(201);
          return response.headers["location"] as string;
        })
        .then((clientUrl: string) => {
          cy.request({
            failOnStatusCode: false,
            method: "PUT",
            url: clientUrl,
            headers: {
              Authorization: `Bearer ${accessToken}`,
              "Content-Type": "application/json",
            },
            body: {
              clientId: renamedClientId,
              enabled: true,
              publicClient: true,
              redirectUris: ["http://localhost/*"],
            },
          }).then((updateResponse) => {
            cy.request({
              method: "DELETE",
              url: clientUrl,
              headers: {
                Authorization: `Bearer ${accessToken}`,
                "Content-Type": "application/json",
              },
            }).then((deleteResponse) => {
              expect(deleteResponse.status).to.eq(204);
              expect(updateResponse.status).to.eq(400);
              expect(updateResponse.body).to.deep.equal({
                error: "invalid_client",
                error_description: 'The Client ID must start with "fleet-". Rejecting request.',
              });
            });
          });
        });
    });
  });

  it("uds-fleet-admin can't delete the non-owned broker client", () => {
    cy.getFleetAdminAccessToken().then((accessToken: string) => {
      const headers = { Authorization: `Bearer ${accessToken}` };
      cy.request({ url: `${CLIENTS_URL}?clientId=broker`, headers, log: false }).then(({ body }) => {
        expect(body).to.have.length(1);
        const client = body[0];
        expect(client.attributes?.["created-by"]).not.to.eq("uds-fleet-admin");
        cy.request({
          method: "DELETE", url: `${CLIENTS_URL}/${client.id}`, headers,
          failOnStatusCode: false, log: false,
        }).then((response) => {
          expect(response.status).to.eq(400);
          expect(response.body).to.deep.equal({
            error: "unauthorized_client",
            error_description: "The Client doesn't have the created-by=uds-fleet-admin attribute. Rejecting request.",
          });
        });
      });
    });
  });

  for (const method of ["GET", "POST"]) {
    it(`uds-fleet-admin can't ${method} another client's roles`, () => {
      cy.getAccessToken("UDS_OPERATOR").then((operatorToken) => {
        cy.request({
          method: "POST", url: CLIENTS_URL,
          headers: { Authorization: `Bearer ${operatorToken}` }, log: false,
          body: { clientId: `operator-roles-${Date.now()}`, publicClient: false, enabled: true },
        }).then((created) => {
          nonOwnedClientUrl = created.headers.location as string;
          cy.getFleetAdminAccessToken().then((accessToken: string) => {
            cy.request({
              method, url: `${nonOwnedClientUrl}/roles`,
              ...(method === "POST" ? { body: { name: "unauthorized-role" } } : {}),
              headers: { Authorization: `Bearer ${accessToken}` },
              failOnStatusCode: false, log: false,
            }).its("status").should("eq", 403);
          });
        });
      });
    });
  }

  it("client lists expose the real secret only to its owner", () => {
    const clientId = `fleet-secret-${Date.now()}`;
    cy.getFleetAdminAccessToken().then((accessToken: string) => {
      const headers = { Authorization: `Bearer ${accessToken}` };
      cy.request({
        method: "POST",
        url: CLIENTS_URL,
        headers,
        body: { clientId, enabled: true, publicClient: false },
      }).then((created) => {
        const clientUrl = created.headers.location as string;
        fixtureClientUrl = clientUrl;
        let realSecret: string;
        cy.request({ url: clientUrl, headers, log: false }).then((client) => {
          expect(client.status).to.eq(200);
          expect(client.body.secret).to.be.a("string").and.not.be.empty;
          expect(client.body.secret).not.to.eq("**********");
          realSecret = client.body.secret;
        });
        cy.request({ url: CLIENTS_URL, headers, log: false }).then((clients) => {
          // Establish that the list returns the actual secret to its owner.
          const owned = clients.body.find((client: any) => client.clientId === clientId);
          expect(owned, "owned client visible in list").to.exist;
          expect(owned.secret).to.eq(realSecret);
          const operator = clients.body.find((client: any) => client.clientId === "uds-operator");
          expect(operator, "operator client").to.exist;
          expect(operator.secret, "operator secret masked in client list").to.eq("**********");
        });
        cy.getAccessToken("UDS_OPERATOR").then((operatorToken: string) => {
          // The same stored secret must be masked for a different owner.
          cy.request({
            url: CLIENTS_URL,
            headers: { Authorization: `Bearer ${operatorToken}` },
            log: false,
          }).then((clients) => {
            const nonOwned = clients.body.find((client: any) => client.clientId === clientId);
            expect(nonOwned, "non-owned client visible in list").to.exist;
            expect(nonOwned.secret).to.eq("**********");
          });
        });
      });
    });
  });
  function createOwnedRole() {
    return cy.getFleetAdminAccessToken().then((ownerToken: string) => {
      const headers = { Authorization: `Bearer ${ownerToken}` };
      return cy.request({
        method: "POST", url: CLIENTS_URL, headers, log: false,
        body: { clientId: `fleet-role-${Date.now()}`, publicClient: true },
      }).then((created) => {
        const clientUrl = created.headers.location as string;
        fixtureClientUrl = clientUrl;
        return cy.request({
          method: "POST", url: `${clientUrl}/roles`, headers, log: false,
          body: { name: "test-role", description: "Original role" },
        }).then(() => cy.request({ url: `${clientUrl}/roles/test-role`, headers, log: false }))
          .then((role) => ({
            ownerToken,
            namedRoleUrl: `${clientUrl}/roles/test-role`,
            roleUrl: `https://keycloak.admin.uds.dev/admin/realms/uds/roles-by-id/${role.body.id}`,
          }));
      });
    });
  }

  it("non-owners cannot read, update, or delete client roles through either route", () => {
    createOwnedRole().then(({ ownerToken, namedRoleUrl, roleUrl }) => {
      cy.getAccessToken("UDS_OPERATOR").then((token: string) => {
        for (const url of [namedRoleUrl, roleUrl]) {
          for (const method of ["GET", "PUT", "DELETE"]) {
            cy.request({
              method, url, headers: { Authorization: `Bearer ${token}` },
              ...(method === "PUT" ? { body: { name: "test-role", description: "Unauthorized update" } } : {}),
              failOnStatusCode: false, log: false,
            }).its("status").should("eq", 403);
          }
        }
        cy.request({ url: roleUrl, headers: { Authorization: `Bearer ${ownerToken}` }, log: false })
          .its("body.description").should("eq", "Original role");
      });
    });
  });

  it("owner can read, update, and delete a client role by ID", () => {
    createOwnedRole().then(({ ownerToken, roleUrl }) => {
      const headers = { Authorization: `Bearer ${ownerToken}` };
      cy.request({ url: roleUrl, headers, log: false }).its("status").should("eq", 200);
      cy.request({
        method: "PUT", url: roleUrl, headers, log: false,
        body: { name: "test-role", description: "Authorized update" },
      }).its("status").should("eq", 204);
      cy.request({ url: roleUrl, headers, log: false })
        .its("body.description").should("eq", "Authorized update");
      cy.request({ method: "DELETE", url: roleUrl, headers, log: false })
        .its("status").should("eq", 204);
    });
  });

});
