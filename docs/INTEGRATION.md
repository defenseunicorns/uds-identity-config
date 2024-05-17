# Integration Testing For UDS Identity Config + UDS Core
[Cypress Web Flow/Integration Testing Docs](https://docs.cypress.io/guides/overview/why-cypress)

## Implemented Tests
| Test Name (link) | Test Description |
|------------------|------------------|
| [Login Existing User](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/e2e/login.cy.ts) | Login in existing user that is created in the testing [realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/realm.json) |
| [Login Nonexistant User / Incorrect creds](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/e2e/login.cy.ts) | User cannot login / authenticate with incorrect creds or without account |
| [Successfuly CAC Registration](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/e2e/registration.cy.ts) | New user can successfully register with CAC |
| [CAC User Login](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/e2e/registration.cy.ts) | New user can successfully login with CAC |
| [Duplicate User Registration](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/e2e/registration.cy.ts) | User cannot register more than once |
| [Password check for special characters](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/e2e/registration.cy.ts) | User registration requires password special characters |
| [Password check for length](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/e2e/registration.cy.ts) | User registration requires password length check |

## Cypress Testing
Using uds-cli task [`uds-core-integration-tests`](https://github.com/defenseunicorns/uds-identity-config/blob/main/tasks.yaml). 

Task explanation:
  - Cleanup an existing uds-core directory ( mainly for local testing )
  - Create docker image that uses the new certs as well as a testing realm.json ( has a defined user, no MFA, and no email verification )
  - Clone [`uds-core`](https://github.com/defenseunicorns/uds-core) necessary for setting up k3d cluster to test against
  - Use that cacert in deploying `uds-core` [istio gateways](https://github.com/defenseunicorns/uds-core/tree/main/src/istio/values)
  - Create zarf package that combines uds-core and identity-config
  - Setup k3d cluster by utilizing `uds-core` (istio, keycloak, pepr, zarf)
  - Deploy zarf package that was created earlier
  - Run cypress tests against deployed cluster

## Updating Cypress Certs
Cypress testing requires that a ca.cer be created and put into an authorized_certs.zip, done by using the `regenerate-test-pki` uds task, which is then utilized by the [Dockerfile](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/Dockerfile). Once a docker image has been created another command is used for pulling that cacert, uds task `cacert`, from the image using it's value to configure uds-core's gateways, `uds-core-gateway-cacert` uds task . Eventually cypress will require a [pfx cert](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/test/cypress/cypress.config.ts) for its CAC testing. 

Our cypress testing utilizes [static certs](https://github.com/defenseunicorns/uds-identity-config/tree/main/src/test/cypress/certs) that are created and saved to limit the need for constantly rebuilding and importing those certs.

Follow these steps to update the certs for cypress:
1. Run `uds run regenerate-test-pki` to regenerate the necessary certs and authorized_certs.zip
2. Run `docker build --build-arg CA_ZIP_URL="authorized_certs.zip" -t uds-core-config:keycloak --no-cache src` to create docker image
3. Run `uds run cacert` to extract cacert from docker image for the tls_cacert.yaml file
4. Copy the authorized_certs.zip, test.pfx, and tls_cacert.yaml into the [certs directory](https://github.com/defenseunicorns/uds-identity-config/tree/main/src/test/cypress/certs)
   - `mv test.pfx tls_cacert.yaml src/authorized_certs.zip src/cypress/certs/`