# Integration Testing For UDS Identity Config + UDS Core

[Cypress Web Flow/Integration Testing Docs](https://docs.cypress.io/guides/overview/why-cypress)

## Implemented Tests

| Test Name (link) | Test Description |
|------------------|------------------|
| [Login Existing User](../src/cypress/e2e/user_login.cy.ts) | Login in existing user that is created in the testing [realm.json](../src/cypress/realm.json) |
| [Login Nonexistant User / Incorrect creds](../src/cypress/e2e/user_login.cy.ts) | User cannot login / authenticate with incorrect creds or without account |
| [Successfuly CAC Registration](../src/cypress/e2e/user_registration.cy.ts) | New user can successfully register with CAC |
| [Duplicate User Registration](../src/cypress/e2e/user_registration.cy.ts) | User cannot register more than once |
| [Password check for special characters](../src/cypress/e2e/user_registration.cy.ts) | User registration requires password special characters |
| [Password check for length](../src/cypress/e2e/user_registration.cy.ts) | User registration requires password length check |

## Cypress Testing
Using uds-cli task [`uds-core-integration-tests`](../../tasks.yaml). 

Task explanation:
  - Cleanup any of the existing files that are no longer necessary
  - Generate fresh PKI certs using the `regenerate-test-pki` task
  - Create docker image that uses the new certs as well as a testing realm.json ( has a defined user, no MFA, and no email verification )
  - Clone [`uds-core`](https://github.com/defenseunicorns/uds-core) necessary for setting up k3d cluster to test against
  - Retrieve the cacert that the docker image is using
  - Use that cacert in deploying `uds-core` [istio gateways](https://github.com/defenseunicorns/uds-core/tree/main/src/istio/values)
  - Create zarf package that combines uds-core and identity-config
  - Setup k3d cluster by utilizing `uds-core` (istio, keycloak, pepr, zarf)
  - Deploy zarf package that was created earlier
  - Run cypress tests against deployed cluster
