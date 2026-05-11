# UDS Identity Config

UDS Identity Config is an integral part of [UDS Core](https://github.com/defenseunicorns/uds-core) and exclusively supports securing UDS Deployments. It builds the Keycloak configuration image (realm, plugins, theme, truststore, JARs) consumed by UDS Core's [Identity deployment](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10).

This project lives in a separate repository for technical reasons and code simplicity, primarily to reduce code sprawl in UDS Core. It is not designed, intended, or supported for use outside of UDS. Hardcoded assumptions throughout the codebase make standalone consumption infeasible.

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
   | dev-cacert          | Get the CA cert value for the Istio Gateway from your local build |
   | debug-istio-traffic | Debug Istio traffic on keycloak             |
   | regenerate-test-pki | Generate a PKI cert for testing             |
   | uds-core-integration-test | Create cluster and deploy uds-core identity using local uds-identity-config image |
   | uds-core-registration-integration-test | Web flow registration integration test |

## Customizing UDS Identity Config

If the default realm, plugin, theme, truststore, or jars do not provide enough functionality ( or provide too much functionality ), take a look at the [customization](./docs/reference/uds-core/idam/customization.md) docs for making changes to the identity config.


## Upgrading Identity Config
When upgrading the Identity Config version, check the [Version Upgrade](./docs/reference/UDS%20Core/IdAM/upgrading-versions.md) docs for help.
