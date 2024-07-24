---
title: Upgrading Versions
type: docs
weight: 3
---

This doc contains important information for upgrading uds-identity-config versions. It is not meant to be an exhaustive list of changes between versions, rather information and steps required to manually upgrade versions without a full redeploy of keycloak.

## v0.5.1 to v0.6.0

<details open>
<summary>Upgrade Details</summary>

This version upgrade implements MFA everywhere. This means anytime a user is registered ( by any means: x509, SSO, username/password ) they will be required to setup MFA or upon next login.

If a user is attempting to access a Group Restricted application without MFA setup, the Group Authorization plugin will block that user from being able to setup MFA as well as accessing that application.

If upgrading without a full redeploy of keycloak the following changes will be needed:
1. The `realm.json` has changed drastically in this upgrade when it comes to the Keycloak Authentication flows. The following steps can be used to do this with clickops:
   1. In `Authentication` `Flows` update each of the following:

      1. `UDS Authentication` : `Add Step` = `OTP Form` after the `Authentication` but before the `UDS Operator Group Authentication Validation` steps. Make sure the `OTP Form` is `Required`

      2. `UDS Registration` : `Add Step` = `OTP Form` after the `UDS Registration Form` but before the `UDS Operator Group Authentication Validation` steps. Make sure the `OTP Form` is `Required`

      3. `UDS Reset Credentials` : `Add Step` = `OTP Form` after the `Reset Password` but before the `UDS Operator Group Authentication Validation` steps. Make sure the `OTP Form` is `Required`

   2. In `Authentication` `Required Actions` make sure `Configre OTP` is `Enabled` `On` and `Set as default action` is `On` as well

</details>

## v0.5.0 to v0.5.1

<details>
<summary>Upgrade Details</summary>

This version upgrade utilizes built in Keycloak functionality for User Managed Attributes.

{{% alert-note %}}
User managed attributes are only available in Keycloak 24+
{{% /alert-note %}}

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
