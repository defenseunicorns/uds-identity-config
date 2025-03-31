---
title: Upgrading Versions
---

This doc contains important information for upgrading uds-identity-config versions. It is not meant to be an exhaustive list of changes between versions, rather information and steps required to manually upgrade versions without a full redeploy of keycloak.

## v0.11.0+

<details open>
<summary>Upgrade Details</summary>

In uds-identity-config versions v0.11.0+, the UDS Operator can automatically switch to Client Credentials Grant from using the Dynamic Client Registration. The new method works faster, is more reliable and doesn't require storing Registration Tokens in the Pepr Store. It is highly recommended to switch to it, which requires the following steps:
   - Create the `uds-operator` Client:
      - Go to `Clients` > `Client registration` > `Create`
         - Client type: `openid-connect`
         - Client ID: `uds-operator`
         - Client Name: `uds-operator`
         - Click `Next`
         - Client authentication: on
         - Uncheck all Authentications flows except from `Service account roles`
         - Click `Next`
         - Click `Save`
      - Go to `Clients` > `uds-operator` > `Credentials` tab
         - Set `Client Authenticator` to `Client Id and Kubernetes Secret`
         - Click `Save`
   - Configure the UDS Client Policy
      - Go to `Realm Settings` > `Client Policies` > `Profiles`
         - Click `Create Client Profile`
               - Name: `uds-client-profile`
               - Description: `UDS Client Profile`
               - Click `Save`
         - Click `Add Executor`
               - Select `uds-operator-permissions`
               - Click `Add`
      - Go to `Realm Settings` > `Client Policies` > `Policies`
         - Click `Create client policy`
               - Name: `uds-client-policy`
               - Description: `UDS Client Policy`
         - Click `Add condition`
         - Select `any-client`
         - Click `Add`
         - Click `Add client profile`
         - Select `uds-client-profile`
         - Click `Add` (there is a glitch in the UI where it seems all the profiles are selected, but only the selected one is actually chosen)
   - Configure the Client Credentials Authentication Flow
      - Go to `Authentication` > `Flows`
         - Click `clients`
               - Click `Actions` > `Duplicate`
                  - Name: `UDS Client Credentials`
                  - Description `UDS Client Credentials`
                  - Click `Duplicate`
         - Go to `Authentication` > `UDS Client Credentials`
               - Click `Add Step`
                  - Select `Client Id and Kubernetes Secret`
                  - Click `Add`
               - Select `Requirement` and set it to `Alternative`
         - Go to `Authentication`, select three dots on the right side of the panel for `UDS Client Credentials` and select `Bind flows`
               - Select `Client authentication flow`
               - Click `Save`
   - Verify that everything is configured correctly
      - Deploy a new package or update the existing one
      - Check UDS Operator logs and verify if there are no errors
         - Use `uds zarf tools kubectl logs deploy/pepr-uds-core-watcher -n pepr-system | grep "Client Credentials Keycloak Client is available"` command to verify if the UDS Operator uses the Client Credentials flow.

After introducing the above changes, please ensure all Packages are reconciled correctly and there are no errors. If for some reason you see the UDS Operator throwing errors with `The Client doesn't have the created-by=uds-operator attribute. Rejecting request`, you need to disable the `UDS Client Policy` and give it a bit more time to process all the Packages.

---

In uds-identity-config version 0.11.0 we incorporated some big changes around MFA.
- Previous versions didn't allow for MFA on the X509 Authentication flow. Now that can be configured to required additional factors of authentication. By default this is disabled and will need to be enabled.
- Additionally, we've added support of WebAuthn MFA. This can assume many different forms such as biometrics, passkeys, etc. This also is disabled by default and is only used as an MFA option.

If wanting to configure the MFA everywhere with both OTP and WebAuthn options, the following steps will help to manually configure these options on an upgrade:
1. There is a [new theme for webauthn-authentication](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/theme/login/webauthn-authenticate.ftl) that conditionally removes the register button. This is removed because we assume that since you are doing MFA you have already provided enough details to be identified by Keycloak and don't need to register.
2. The Authentication `Required Actions` have a few changes as well:
   - Click `Authentication` tab from left side menu
   - Click `Required Actions` tab from Authentication page menu
   - Enable the following `Required Actions`, only toggle the `Enabled` **DO NOT TOGGLE** `Set as default action`:
      - `Configure OTP`
      - `Webauthn Register`
      - `Delete Credential`
   - Disable the `WebAuthn Register Passwordless`, make sure this is **not** the `WebAuthn Register` option ( this one should be enabled )
3. The `UDS Authentication` authentication flow has undergone significant changes.
   - Click `Authentication` tab from left side menu
   - Click `UDS Authentication` flow option
   - **This can be very dangerous to modify so make sure you know what you're doing before making changes here**
   - In the `Authentication` top level sub-flow of the `UDS Authentication` flow
      - Click the `+` icon and add a `sub-flow`
         - Name that sub-flow `X509 Authentication`
      - Drag that new sub-flow up and drop below the `Cookie` and the `IDP Redirector` step
      - Set the flow to `Alternative`
      - in the new `X509 Authentication` sub-flow select the `+` icon and add a sub-flow called `X509 Conditional OTP`
         - Set the `X509 Conditional OTP` to `Required`
         - Click the `+` and add the `Condition` called `Condition - user configured`
            - set this to be `Required`
         - Click the `+` and add the step called `OTP Form`
            - set this to be `Required`
         - Click the `+` and add the step called `WebAuthn Authenticator`
      - Drag the existing `X509/Validate Username Form` step into the `X509 Authentication` sub-flow, should be above the `X509 Conditional OTP`
         - May have to drag this twice, make sure this is `Required`

---

To add an `IDP Redirector` option to the `UDS Authentication`, which enables bypassing the login page and jumping directly to the IDP login when using the `kc_idp_hint` URL parameter, do the following steps:
- Click `Authentication` from the left sidebar under `Configure`
- Select the `UDS Authentication` auth flow
- Under the `Authentication` sub-flow in `UDS Authentication`, click the `+` and add a new `sub-flow`
   - Name that sub-flow `idp redirector`
   - click `Add`
- Drag that new `idp redirector` sub-flow from the bottom of the `Authentication` sub-flow to be directly below the `Cookie` step
- Set the `idp redirector` sub-flow to be `Alternative`
- Click the `+` on the `idp redirector` sub-flow and add a new step
- Select the `Identity Provider Redirector`
- Click `Add`
- Set that `Identity Provider Redirector` step to `Required`

</details>

## v0.10.0+

<details>
<summary>Upgrade Details</summary>

In uds-identity-config versions 0.10.0+, the version of Keycloak was upgraded to Keycloak 26.1.0. In this release of Keycloak an unmentioned breaking change that added case sensitivity to the Client SAML Mappers. This resulted in breaking SAML Auth flows due to users IDP data not being correctly mapped into applications ( ex. Sonarqube, Gitlab, etc ). Manual steps to fix this issue:
   - Click `Client scopes`
   - For each of the following mappers:
      - `mapper-saml-email-email`
      - `mapper-saml-firstname-first_name`
      - `mapper-saml-lastname-last_name`
      - `mapper-saml-username-login`
      - `mapper-saml-username-name`
   - Select the mapper, should now be on the `Client scope details` page
   - Select the `Mappers` tab
   - Select the available mapper
   - Manually change the `Property` field dropdown to match the designated mapper property
      - `mapper-saml-email-email` had a value of `Email`, that needs to be changed to select the `email` option from the drop down.
      - `mapper-saml-firstname-first_name` had a value of `FirstName`, that needs to be changed to select the `firstName` option from the drop down.
      - `mapper-saml-lastname-last_name` had a value of `LastName`, that needs to be changed to select the `lastName` option from the drop down.
      - `mapper-saml-username-login` had a value of `Username`, that needs to be changed to select the `username` option from the drop down.
      - `mapper-saml-username-name` had a value of `Username`, that needs to be changed to select the `username` option from the drop down.
   - Make sure and click `Save` after updating the property field
</details>

## v0.9.1 to v0.10.0

<details>
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

<details>
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

<details>
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
