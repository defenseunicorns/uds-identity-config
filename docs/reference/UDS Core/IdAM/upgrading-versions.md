---
title: Upgrading Versions
---

This doc contains important information for upgrading uds-identity-config versions. It is not meant to be an exhaustive list of changes between versions, rather information and steps required to manually upgrade versions without a full redeploy of keycloak.

## v0.9.1 to v0.10.0

<details open>
<summary>Upgrade Details</summary>

* For running Istio with Ambient Mesh, it is required to add two new entries to the trusted hosts list: `*.pepr-uds-core-watcher.pepr-system.svc.cluster.local` and `*.keycloak.svc.cluster.local`. This is done automatically for new deployments but when upgrading it is required to perform these extra steps:
  - Click `Clients` > `Client registration` > `Client details`
  - Add `*.pepr-uds-core-watcher.pepr-system.svc.cluster.local` and `*.keycloak.svc.cluster.local` to the `Trusted Hosts` list
  - Click `Save`
* Keycloak 26.1.1 introduces a new option to force re-login after resetting credentials ([Keycloak Release Notes](https://www.keycloak.org/docs/latest/release_notes/index.html#new-option-in-send-reset-email-to-force-a-login-after-reset-credentials)). This option has been enabled for new deployments but the existing ones, it needs to be turned on manually:
    - Click `Authentication` > `UDS Reset Credentials` and find `Send Reset Email` Step of the Authentication Flow.
    - Click `Settings`, enter a new alias name, for example `reset-credentials-email` and turn the `Force login after reset` option on.
    - Click `Save`
</details>

## v0.5.1 to v0.5.2

<details open>
<summary>Upgrade Details</summary>

* An custom Keycloak event logger that replaces the default event logger is [included in this release](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/realm.json#L1669), if you wish to enable manually as part of an upgrade do the following (in the `Unicorn Delivery Service` realm):
  - Click on the `Realm Settings` > `Events` and add `jsonlog-event-listener`.
  - Remove the built in `jboss-logging` event listener.
  - Click `Save`
* The custom registration event listener was [renamed](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/realm.json#L1670) from `custom-registration-listener` to `registration-event-listener`. To manually update this event listener (in the `Unicorn Delivery Service` realm):
  - Click on the `Realm Settings` > `Events` and add `registration-event-listener`.
  - Remove `custom-registration-listener`.
  - Click `Save`
* An additional scope (`bare-groups`) was included in the uds [realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/realm.json#L1608-L1636). To add this scope manually do the following (in the `Unicorn Delivery Service` realm):
   - Click on `Client Scopes` > `Create client scope`.
   - Name the scope `bare-groups`, and configure it  to be
      - Type: `Optional`
      - Include in token scope: `On`
   - Click `Save`
   - Click `Mappers` > `Create a new mapper`
   - Select `Custom Group Path Mapper` and name it `bare groups`
   - To enable this scope to be added as a `defaultClientScope` for your clients, navigate to the top level `Clients` > `Client registration` tab.
      - Click `Allowed Client Scopes`
      - Add `bare-groups` to the list of `Allowed Client Scopes`
      - Click `Save`
</details>

## v0.5.0 to v0.5.1

<details open>
<summary>Upgrade Details</summary>

This version upgrade utilizes built in Keycloak functionality for User Managed Attributes.

:::note
User managed attributes are only available in Keycloak 24+
:::

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

## v0.4.5 to v0.5.0
<details>
<summary>Upgrade Details</summary>
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
