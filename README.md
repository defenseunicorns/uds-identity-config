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


## Upgrading Identity Config
<details open>
<summary><b>From v0.5.0 to v0.5.1</b></summary>

This version upgrade utilizes built in Keycloak functionality for User Managed Attributes.

> [!IMPORTANT]  
> User managed attributes are only available in Keycloak 24+

If upgrading without a full redeploy of keycloak the following changes will be needed:
1. The `realm.json` will need to be updated to contain the correct User Managed Attributes definition, [User Managed Attributes Configuration](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.1/src/realm.json#L1884-L1895). The following steps can be used to do this with clickops:
   1. In `Realm Settings` tab and on the `General` page
      1. toggle off `User-managed access`
      2. `Unmanaged Attributes` set to `Only administrators can write`
   2. On `User profile` page
      1. select the `JSON Editor` tab
      2. Copy and Paste the value of [the User Attribute Definition from the realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.1/src/realm.json#L1891)
      3. `Save`
2. Incorporate STIG password rules, in accordance with these two hardening guides:
   * [Elasticsearch 8.0 Application Server](https://github.com/user-attachments/files/16178987/Elasticsearch.8.0.Hardening.Guide.Application.Server.SRG.V3R1.pdf)
   * [Elasticsearch 8.0 Central Log Server](https://github.com/user-attachments/files/16178988/Elasticsearch.8.0.Hardening.Guide.Central.Log.Server.SRG.V2R1.pdf)
   * Changes:
     1. Passwords expire in 60 days
     2. Passwords complexity: 2 special characters, 1 digit, 1 lowercase, 1 uppercase, and 15 character minimum length
     3. IDP session idle timeout is now 10 minutes
     4. Maximum login attempts is now 3
</details>

<details>
<summary><b>From v0.4.5 to v0.5.0</b></summary>
This version upgrade brings in a new Authentication Flow for group authorization.

If upgrading without a full redeploy of keycloak the following steps will be necessary to create and use group authorization:
1. In keycloak admin portal, in `UDS` realm, navigate to `Authentication` sidebar tab
2. In `Authentication` tab add the `Authorization` flow to `UDS Authentication`, `UDS Registration`, `UDS Reset Credentials`
   1. In each `Authentication` flow
      1. `Add step` -> `UDS Operator Group Authentication Validation`
      * Make sure that the step is at the base level and bottom of the Authentication flow
3. Finally if using `SAML` IDP
   1. In the `Authentication` tab
      1. `Create Flow`
      2. `Name` -> `Authorization`
      3. `Description` -> `UDS Operator Group Authentication Validation`
      4. `Basic Flow`
      5. `Create`
      6. `Add execution`
      7. `Add` the `UDS Operator Group Authentication Validation`
   2. In the `Identity Providers` tab, select the `SAML` Provider
      1. Add the `Authorization` flow to the `Post login flow` in the `Advanced settings` section
</details>
