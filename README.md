# UDS Identity Config

This repo builds the UDS Identity (Keycloak) Config image used by UDS Identity. Utilize this repository to create your own Keycloak config image for customizing `uds-core`'s [Identity deployment](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10).

## UDS Tasks

   Available tasks used by `uds run <task name>`, also can be viewed via command line by running `uds run --list`.

   | Task Name | Task Description |
   |---------------------|---------------------------------------------|
   | build-and-publish   | Build and publish the multi-arch image      |
   | build-zarf-pkg      | Build the image locally and package it with Zarf |
   | dev-build           | Build the image locally for dev             |
   | dev-update-image    | Build the image and import locally into k3d |
   | dev-theme           | Copy theme to Keycloak in dev cluster       |
   | dev-plugin          | Build and run unit tests for keycloak plugin|
   | cacert              | Get the CA cert value for the Istio Gateway |
   | debug-istio-traffic | Debug Istio traffic on keycloak             |
   | regenerate-test-pki | Generate a PKI cert for testing             |
   | uds-core-integration-test | Create cluster and deploy uds-core identity using local uds-identity-config image |
   | uds-core-registration-integration-test | Web flow registration integration test |

## Customizing UDS Identity Config

If the default realm, plugin, theme, truststore, or jars do not provide enough functionality ( or provide too much functionality ), take a look at the [CUSTOMIZE.md](./docs/CUSTOMIZE.md) docs for making changes to the identity config.
