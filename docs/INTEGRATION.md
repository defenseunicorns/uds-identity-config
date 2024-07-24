# Integration Testing For UDS Identity Config + UDS Core

[Cypress Web Flow/Integration Testing Docs](https://docs.cypress.io/guides/overview/why-cypress)

Integration tests are split into two categories:
* Username / Password and Group Authentication testing
* X509 Testing

This is necessary for avoiding conflicting tests when an X509 cert is present, by seperating the two we can test the usecases in an environment that is more realistic.

## File Structure / Explanation

All integration testing files are found in the `src/test/cypress` directory.

There is two config files that modify the environment for testing between X509 and Username/Password. These config files use the `specPattern` to define the test files the config is relavant to.

If in the `src/test/cypress` directory, `npm` command can be used to run these tests against a deployed cluster.
  * `npm run cy.open.x509` : Open Cypress UI and run X509 tests
  * `npm run cy.open.noX509` : Open Cypress UI and run Username / Password tests
  * `npm run cy.run.x509` : Run Cypress X509 tests from terminal
  * `npm run cy.run.noX509` : Run Cypress Username / Password tests from terminal
  * `npm run cy.run` : Run both Cypress X509 and Username / Password tests from terminal

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

## Implemented Tests

### X509 Integration Tests

**[X509 Cypress Config](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/cypress.config.x509.ts)**

| Name | Description |
|------|-------------|
| [Register New User - Success](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/x509/x509.cy.ts) | Use X509 Cert to register new user |
| [Login New User - Success](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/x509/x509.cy.ts) | Login with the newly created user |

### Username / Password Integration Tests

**[Username / Password Cypress Config](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/cypress.config.noX509.ts)**

| Name | Description |
|------|-------------|
| [User Registration - Success](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Register user with Username / Password form |
| [User Login - Success](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Login newly created user |
| [Invalid Password Login - Failure](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Fail authentication due to incorrect credentials |
| [Invalid Duplicate User - Failue](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Fail registration because user already exists |
| [Invalid Password Length - Failue](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Fail registration because password doesn't meet 15 character requirement |
| [Invalid Password Complexity ( Special Characters ) - Failure](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Fail registration because password doesn't meet 2 special characters requirement |
| [Invalid Password Complexity ( Digits ) - Failure](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Fail registration because password doesn't meet 1 digit requirement |
| [Invalid Password Complexity ( Uppercase ) - Failure](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Fail registration because password doesn't meet 1 uppercase character requirement |
| [Invalid Password Complexity ( Lowercase ) - Failure](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/username-password.cy.ts) | Fail registration because password doesn't meet 1 lowercase character requirement |

### UDS Group Authorization Integration Tests

| Name | Description |
|------|-------------|
| [Grafana Admin User - Success ](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/group-authz.cy.ts) | Admin users should have access to custom Grafana deployment |
| [Grafana Auditor User - Failure ](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/test/cypress/e2e/noX509/group-authz.cy.ts) | Auditor users should **not** have access to custom Grafana deployment |
