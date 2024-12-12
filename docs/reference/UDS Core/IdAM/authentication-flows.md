---
title: Authentication Flow Customization
tableOfContents:
  maxHeadingLevel: 5
---

# Authentication Flow Customization

:::note Environment variables configured in the [uds-core Keycloak values.yaml file](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L30-32) have `REALM_` appended to them during creation. See [ Customization docs](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/) for more information.:::

:::warning If upgrading uds-core, be aware that Keycloak Admin manual configuration will probably be required to set new Realm values. See the clickops section below for how to do this. :::

## Bundle Overrides
To simplify the configuration of the available authentication flows, the following three environment variables have been exposed. These variables default to `true` in UDS Core, override their values in a bundle to disable.

:::note These settings allow for enabling/disabling one or more of the Auth flows. Be aware that disabling all three will result in no options for registration and authentication (login). :::

| Setting | Description | Options |
| - | - | - |
| [USERNAME_PASSWORD_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L30) | Toggle on/off the Username and Password Authentication flow. When disabled there will be no username password login, password / password confirm registration fields, no credential reset, and no update password options available. | `true`(default), `false` |
| [X509_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L31) | Toggle on/off X509 (CAC) Authentication flow. | `true`(default), `false` |
| [SOCIAL_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L32) | Toggle on/off Social (Google SSO, Azure AD, etc. ) Authentication flows.| `true`(default), `false` |

These three variables handle the complexities of configuring the following environment variables, which are responsible for both visual (theme) and security (realm). The following variables are not exposed for overriding.

### Theme Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [ENABLE_SOCIAL_LOGIN](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L21) | Control whether Social Login block is included on the login page. | `true`(default), `false`|
| [ENABLE_X509_LOGIN](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L22) | Control whether X509 ( CAC ) Login block is included on the login and registration pages. | `true`(default), `false`|
| [ENABLE_USERNAME_PASSWORD_AUTH](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L23) | Control whether Username Password Login block is included on the login and registration pages. This will also control the realm configuration for updating passwords or setting a new password from users account management. | `true`(default), `false`|
| [ENABLE_REGISTER_BUTTON](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L24) | Control whether the register button is included on the login page. | `true`(default), `false`|

### Realm Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [ENABLE_DENY_USERNAME_PASSWORD](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L25) | Control a [`Deny Access`](https://github.com/defenseunicorns/uds-identity-config/blob/rework-auth-flows/src/realm.json#L2259-L2266) flow in the [`MFA Login`](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json#L2243-L2276) flow that determines if Username Password can be used to login. | `REQUIRED`, `DISABLED`(default) |
| [ENABLE_RESET_CREDENTIAL_FLOW](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L26) | Control whether a the Reset Credential Auth Flow can be reached by user to reset or set their password. | `REQUIRED`(default), `DISABLED` |
| [ENABLE_REGISTRATION_FORM](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L27) | Control whether the registration form can be reached for a new registration. | `REQUIRED`(default), `DISABLED` |
| [ENABLE_REALM_OTP](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L28) | Control whether One Time Password is allowed. | `true`(default), `false` |

### Common Configurations

At this time, UDS Core supports three different avenues of authentication for users. This means three different ways to register and/or login. Below are common Authentication configurations.

#### In Depth Configuration Map
| Authentication Configuration Description | Theme Configurations | Realm Configurations |
| - | - | - |
| Default | `ENABLE_SOCIAL_LOGIN: true`<br>`ENABLE_X509_LOGIN: true`<br>`ENABLE_USERNAME_PASSWORD_AUTH: true`<br>`ENABLE_REGISTER_BUTTON: true` | `ENABLE_DENY_USERNAME_PASSWORD: DISABLED`<br>`ENABLE_RESET_CREDENTIAL_FLOW: REQUIRED`<br>`ENABLE_REGISTRATION_FORM: REQUIRED`<br>`ENABLE_REALM_OTP: true` |
| Username Password Only | `ENABLE_SOCIAL_LOGIN: false`<br>`ENABLE_X509_LOGIN: false`<br>`ENABLE_USERNAME_PASSWORD_AUTH: true`<br>`ENABLE_REGISTER_BUTTON: true` | `ENABLE_DENY_USERNAME_PASSWORD: DISABLED`<br>`ENABLE_RESET_CREDENTIAL_FLOW: REQUIRED`<br>`ENABLE_REGISTRATION_FORM: REQUIRED`<br>`ENABLE_REALM_OTP: true` |
| Social (IDP) Only | `ENABLE_SOCIAL_LOGIN: true`<br>`ENABLE_X509_LOGIN: false`<br>`ENABLE_USERNAME_PASSWORD_AUTH: false`<br>`ENABLE_REGISTER_BUTTON: false` | `ENABLE_DENY_USERNAME_PASSWORD: REQUIRED`<br>`ENABLE_RESET_CREDENTIAL_FLOW: DISABLED`<br>`ENABLE_REGISTRATION_FORM: DISABLED`<br>`ENABLE_REALM_OTP: false` |
| X509 Only | `ENABLE_SOCIAL_LOGIN: false`<br>`ENABLE_X509_LOGIN: true`<br>`ENABLE_USERNAME_PASSWORD_AUTH: false`<br>`ENABLE_REGISTER_BUTTON: true` | `ENABLE_DENY_USERNAME_PASSWORD: REQUIRED`<br>`ENABLE_RESET_CREDENTIAL_FLOW: DISABLED`<br>`ENABLE_REGISTRATION_FORM: REQUIRED`<br>`ENABLE_REALM_OTP: false` |
| Username Password with X509 | `ENABLE_SOCIAL_LOGIN: false`<br>`ENABLE_X509_LOGIN: true`<br>`ENABLE_USERNAME_PASSWORD_AUTH: true`<br>`ENABLE_REGISTER_BUTTON: true` | `ENABLE_DENY_USERNAME_PASSWORD: DISABLED`<br>`ENABLE_RESET_CREDENTIAL_FLOW: REQUIRED`<br>`ENABLE_REGISTRATION_FORM: REQUIRED`<br>`ENABLE_REALM_OTP: true` |
| Username Password with Social (IDP) | `ENABLE_SOCIAL_LOGIN: true`<br>`ENABLE_X509_LOGIN: false`<br>`ENABLE_USERNAME_PASSWORD_AUTH: true`<br>`ENABLE_REGISTER_BUTTON: true` | `ENABLE_DENY_USERNAME_PASSWORD: DISABLED`<br>`ENABLE_RESET_CREDENTIAL_FLOW: REQUIRED`<br>`ENABLE_REGISTRATION_FORM: REQUIRED`<br>`ENABLE_REALM_OTP: true` |
| X509 with Social (IDP) | `ENABLE_SOCIAL_LOGIN: true`<br>`ENABLE_X509_LOGIN: true`<br>`ENABLE_USERNAME_PASSWORD_AUTH: false`<br>`ENABLE_REGISTER_BUTTON: true`| `ENABLE_DENY_USERNAME_PASSWORD: REQUIRED`<br>`ENABLE_RESET_CREDENTIAL_FLOW: DISABLED`<br>`ENABLE_REGISTRATION_FORM: REQUIRED`<br>`ENABLE_REALM_OTP: false` |

## Manual Configuration

### Theme Configurations
Theme's cannot be clickops'ed, for these changes to take affect an upgrade or fresh deployment will be required. Another option is exec-ing into the the keycloak pod and copying in the new themes to the `/opt/keycloak/theme/themes/login/` directory. After copying in the theme changes, the theme changes depend on environment variables being defined in the [theme.properties file](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/theme/login/theme.properties). The above table demonstrates the different environment variables for the `theme.properties` file.

### Realm Configurations
All Realm Configurations require accesss to the Keycloak admin portal.

| Configuration | How to Configure |
| - | - |
| `ENABLE_DENY_USERNAME_PASSWORD` | 1. Realm Authentication tab<br> 2. Select the `UDS Authentication` Authentication Flow<br> 3. `DISABLE` the `Deny Access` step that is below the `Username Password Form` |
| `ENABLE_RESET_CREDENTIAL_FLOW` | 1. Realm Authentication tab<br> 2. Select the `UDS Reset Credentials` Authentication Flow<br> 3. `DISABLE` the `Reset Password` step |
| `ENABLE_REGISTRATION_FORM` | 1. Realm Authentication tab<br> 2. Select the `UDS Registration` Authentication Flow<br> 3. `DISABLE` the `UDS Registration form` step |
| `ENABLE_REALM_OTP` | 1. Realm Authentication tab<br> 2. Select the `Required Action` tab at the top of the Authentication view<br> 3. Toggle off the `Configure OTP` |
