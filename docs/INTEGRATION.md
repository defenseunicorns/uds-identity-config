# Integration Testing For UDS Identity Config + UDS Core

[Playwright Web Flow/Integration Testing Docs](https://playwright.dev/docs/intro)

## Tests

| Test Name (link) | Test Description |
|------------------|------------------|
| [Login Existing User](../src/test/tests/UserLogin.spec.ts) | User can successfully login and authenticate with Username / Password |
| [Login Nonexistant User / Incorrect creds](../src/test/tests/UserLogin.spec.ts) | User cannot login / authenticate with incorrect creds or without account |Integration |
| [Successfuly Registration](../src/test/tests/UserRegistration.spec.ts) | New user can successfully register |
| [Duplicate User Registration](../src/test/tests/UserRegistration.spec.ts) | User cannot register more than once |
| [New user can login](../src/test/tests/UserRegistration.spec.ts) | New user can successfully login |
| [Password check for special characters](../src/test/tests/UserRegistration.spec.ts) | User registration requires password special characters |
| [Password check for length](../src/test/tests/UserRegistration.spec.ts) | User registration requires password length check |
| [Password check for length](../src/test/tests/UserRegistration.spec.ts) | User registration requires password length check | 


## User Registration
Using task [`uds-core-integration-registration-test`](../../tasks.yaml), which tests the User registration webflow.

#### Understanding the `uds-core-integration-registration-test` task
 - generate test pki
     - use csr.conf ( maybe i shouldn't bring this in? seems like the simplest solution )
    - `- task: regenerate-test-pki`
 - update dockerfile to use the test pki & build
    - `- cmd: docker build --build-arg CA_ZIP_URL="https://example.com/new-ca-zip-file.zip" -t your_image_name .`
 - update uds-core to use that image
    - `- cmd: git clone https://github.com/defenseunicorns/uds-core.git`
 - update cacert for gateways
    - `- cmd: uds run -f uds-core/src/keycloak/tasks.yaml dev-cacert`
 - create uds-core package from uds-identity zarf.yaml definition ( pulls in local identity config and values.yaml ) 
    - `- cmd: uds zarf package create . --confirm`
 - deploy k3d-core-istio-dev bundle (ISTIO ZARF PEPR)
   - `- cmd: uds deploy ghcr.io/defenseunicorns/packages/uds/bundles/k3d-core-istio-dev:0.16.1 --confirm`
 - deploy uds-core with that image
    - `- cmd: uds zarf package deploy zarf-package-identity-* --confirm`
 - verify access to sso.uds.dev & keycloak.admin.uds.dev
    - validations:
    ```yaml
    actions:
      onDeploy:
        after:
          - description: Validate Identity Deployment
            maxTotalSeconds: 300
            wait:
              cluster:
                kind: Packages
                name: keycloak
                namespace: keycloak
                condition: "'{.status.phase}'=Ready"
          - description: Validate Keycloak Container
            maxTotalSeconds: 300
            wait:
              cluster:
                kind: Pods
                name: keycloak-0
                namespace: keycloak
                condition: ContainersReady
          - description: Validate admin interface
            wait:
              network:
                protocol: https
                address: keycloak.admin.uds.dev
                code: 200
          - description: Validate public interface
            wait:
              network:
                protocol: https
                address: sso.uds.dev
                code: 200
    ```
 - Playwright web flow tests
    - Uses a different [realm.json](../src/test/realm.json) that is customized for testing ( no email verification or OTP ).
  

## TODOs before PR

    - need to add git clone back into registration integration task once first todo is finished
  

## Questions
    - should we be testing across different browsers? is there a specific browser we should focus?
      - this will create a tricky scenario of needing to also delete users or each test creates its own user