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
  before(() => {
    cy.exec(`uds zarf tools kubectl apply -f ${SETUP_MANIFEST}`)
      .its("exitCode")
      .should("eq", 0);
  });

  after(() => {
    cy.exec(`uds zarf tools kubectl delete -f ${SETUP_MANIFEST} --ignore-not-found`)
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

  it("uds-fleet-admin can't delete a Client it didn't create", () => {
    cy.getFleetAdminAccessToken().then((accessToken: string) => {
      cy.request({
        method: "GET",
        url: CLIENTS_URL,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
      }).then((response) => {
        expect(response.status).to.eq(200);
        // "broker" is a built-in client that uds-fleet-admin did not create.
        const brokerClient = response.body.find((client: any) => client.clientId === "broker");
        expect(brokerClient, "broker client visible in list").to.exist;
        expect(brokerClient.attributes?.["created-by"], "broker not owned by uds-fleet-admin").to.not.eq("uds-fleet-admin");
        const clientUrl = `${CLIENTS_URL}/${brokerClient.id}`;
        cy.request({
          failOnStatusCode: false,
          method: "DELETE",
          url: clientUrl,
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
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
});
