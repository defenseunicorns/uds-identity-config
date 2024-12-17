---
title: Authentication Flow Customization
tableOfContents:
  maxHeadingLevel: 5
---

# Authentication Flow Customization

:::note
Environment variables configured in the [uds-core Keycloak values.yaml file](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L30-32) have `REALM_` appended to them during creation. See [ Customization docs](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/) for more information.
:::

:::warning
If upgrading uds-core, be aware that Keycloak Admin manual configuration will probably be required to set new Realm values. See the manual configuration section below for how to do this.
:::

## Bundle Overrides
To simplify the configuration of the available authentication flows, the following three environment variables have been exposed. These variables default to `true` in UDS Core, override their values in a bundle to disable.

:::note
These settings allow for enabling/disabling one or more of the Auth flows. Be aware that disabling all three will result in no options for registration and authentication (login).
:::

| Setting | Description | Options |
| - | - | - |
| [USERNAME_PASSWORD_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L30) | Toggle on/off the Username and Password Authentication flow. When disabled there will be no username password login, password / password confirm registration fields, no credential reset, and no update password options available. | `true`(default), `false` |
| [X509_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L31) | Toggle on/off X509 (CAC) Authentication flow. | `true`(default), `false` |
| [SOCIAL_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L32) | Toggle on/off Social (Google SSO, Azure AD, etc. ) Authentication flows.| `true`(default), `false` |

These three variables handle the complexities of configuring the following environment variables, which are responsible for both visual (theme) and security (realm). The following variables are not exposed for overriding.

## Manual Configuration

### Theme Configurations
Theme's cannot be clickops'ed, for these changes to take affect an upgrade or fresh deployment will be required. Another option is exec-ing into the the keycloak pod and copying in the new themes to the `/opt/keycloak/theme/themes/login/` directory. After copying in the theme changes, the theme changes depend on environment variables being defined in the [theme.properties file](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/theme/login/theme.properties). The above table demonstrates the different environment variables for the `theme.properties` file.

### Realm Configurations
All Realm Configurations require accesss to the Keycloak admin portal.

| Configuration | How to Configure |
| - | - |
| `DENY_USERNAME_PASSWORD_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `UDS Authentication` Authentication Flow<br> 3. `DISABLE` the `Deny Access` step that is below the `Username Password Form` |
| `RESET_CREDENTIAL_FLOW_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `UDS Reset Credentials` Authentication Flow<br> 3. `DISABLE` the `Reset Password` step |
| `REGISTRATION_FORM_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `UDS Registration` Authentication Flow<br> 3. `DISABLE` the `UDS Registration form` step |
| `OTP_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `Required Action` tab at the top of the Authentication view<br> 3. Toggle off the `Configure OTP` |
