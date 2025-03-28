# In Depth Keycloak Authentication Flow Breakdown

## Keycloak Authentication Flows
See the [Authentication Flow Customization](../reference/UDS%20Core/IdAM/authentication-flows.md) doc for an explanation of the three toggles that are used for configuring different Authentication flow scenarios. The following maps what those toggles do to the actual Realm and Theme settings used within the uds-identity-config.

### Theme Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [SOCIAL_LOGIN_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L21) | Control whether Social Login block is included on the login page. | `true`(default), `false`|
| [X509_LOGIN_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L22) | Control whether X509 ( CAC ) Login block is included on the login and registration pages. | `true`(default), `false`|
| [USERNAME_PASSWORD_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L23) | Control whether Username Password Login block is included on the login and registration pages. This will also control the realm configuration for updating passwords or setting a new password from users account management. | `true`(default), `false`|
| [REGISTER_BUTTON_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L24) | Control whether the register button is included on the login page. | `true`(default), `false`|
| [WEBAUTHN_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L30) | Control whether the `WebAuthn Authenticator` pop-up shows the register new user. This can already be assumed since the WebAuthn is configured as an MFA. This also controls whether a user can delete a credential or not. | `true`, `false`(default) |

### Realm Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [DENY_USERNAME_PASSWORD_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L25) | Control a [`Deny Access`](https://github.com/defenseunicorns/uds-identity-config/blob/rework-auth-flows/src/realm.json#L2259-L2266) flow in the [`MFA Login`](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json#L2243-L2276) flow that determines if Username Password can be used to login. | `REQUIRED`, `DISABLED`(default) |
| [RESET_CREDENTIAL_FLOW_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L26) | Control whether a the Reset Credential Auth Flow can be reached by user to reset or set their password. | `REQUIRED`(default), `DISABLED` |
| [REGISTRATION_FORM_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L27) | Control whether the registration form can be reached for a new registration. | `REQUIRED`(default), `DISABLED` |
| [OTP_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L28) | Control whether One Time Password is allowed. | `true`(default), `false` |
| [OTP_FLOW_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L29) | Control whether the OTP is required as an MFA method. | `REQUIRED`(default), `DISABLED` |
| [WEBAUTHN_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L30) | Control whether the `WebAuthn Register` required action is enabled. | `true`, `false`(default) |
| [WEBAUTHN_FLOW_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L31) | Control whether the `WebAuthn Register` required action is enabled. | `REQUIRED`, `DISABLED`(default) |
| [X509_MFA_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L32) | Control whether X509 Authentication flows can also require MFA. This configuration is used in the custom `Registration Validation` plugin. | `true`, `false`(default) |
| [X509_MFA_FLOW_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L33) | Control whether X509 Authentication flows require MFA. This is needed so that X509 MFA can be configured seperately from Username/Password MFA. | `REQUIRED`, `DISABLED`(default) |
| [MFA_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L34) | Control whether the `MFA` authentication is required. | `true`(default), `false` |
| [MFA_FLOW_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/templates/secret-kc-realm.yaml#L35) | Control whether the `MFA` authentication is required. | `REQUIRED`(default), `DISABLED` |

### Common Configurations

At this time, UDS Core supports three different avenues of authentication for users. This means three different ways to register and/or login. Below are common Authentication configurations.

#### In Depth Configuration Map
| Authentication Configuration Description | Theme Configurations | Realm Configurations |
| - | - | - |
| Default | `SOCIAL_LOGIN_ENABLED: true`<br>`X509_LOGIN_ENABLED: true`<br>`USERNAME_PASSWORD_AUTH_ENABLED: true`<br>`REGISTER_BUTTON_ENABLED: true` | `DENY_USERNAME_PASSWORD_ENABLED: DISABLED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: REQUIRED`<br>`REGISTRATION_FORM_ENABLED: REQUIRED`<br>`OTP_ENABLED: true`<br>`WEBAUTHN_ENABLED: false`<br>`X509_MFA_ENABLED: false` |
| Username Password Only | `SOCIAL_LOGIN_ENABLED: false`<br>`X509_LOGIN_ENABLED: false`<br>`USERNAME_PASSWORD_AUTH_ENABLED: true`<br>`REGISTER_BUTTON_ENABLED: true` | `DENY_USERNAME_PASSWORD_ENABLED: DISABLED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: REQUIRED`<br>`REGISTRATION_FORM_ENABLED: REQUIRED`<br>`OTP_ENABLED: true`<br>`WEBAUTHN_ENABLED: false`<br>`X509_MFA_ENABLED: false` |
| Social (IDP) Only | `SOCIAL_LOGIN_ENABLED: true`<br>`X509_LOGIN_ENABLED: false`<br>`USERNAME_PASSWORD_AUTH_ENABLED: false`<br>`REGISTER_BUTTON_ENABLED: false` | `DENY_USERNAME_PASSWORD_ENABLED: REQUIRED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: DISABLED`<br>`REGISTRATION_FORM_ENABLED: DISABLED`<br>`OTP_ENABLED: false`<br>`WEBAUTHN_ENABLED: false`<br>`X509_MFA_ENABLED: false` |
| X509 Only | `SOCIAL_LOGIN_ENABLED: false`<br>`X509_LOGIN_ENABLED: true`<br>`USERNAME_PASSWORD_AUTH_ENABLED: false`<br>`REGISTER_BUTTON_ENABLED: true` | `DENY_USERNAME_PASSWORD_ENABLED: REQUIRED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: DISABLED`<br>`REGISTRATION_FORM_ENABLED: REQUIRED`<br>`OTP_ENABLED: false`<br>`WEBAUTHN_ENABLED: false`<br>`X509_MFA_ENABLED: false` |
| Username Password with X509 | `SOCIAL_LOGIN_ENABLED: false`<br>`X509_LOGIN_ENABLED: true`<br>`USERNAME_PASSWORD_AUTH_ENABLED: true`<br>`REGISTER_BUTTON_ENABLED: true` | `DENY_USERNAME_PASSWORD_ENABLED: DISABLED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: REQUIRED`<br>`REGISTRATION_FORM_ENABLED: REQUIRED`<br>`OTP_ENABLED: true`<br>`WEBAUTHN_ENABLED: false`<br>`X509_MFA_ENABLED: false` |
| Username Password with Social (IDP) | `SOCIAL_LOGIN_ENABLED: true`<br>`X509_LOGIN_ENABLED: false`<br>`USERNAME_PASSWORD_AUTH_ENABLED: true`<br>`REGISTER_BUTTON_ENABLED: true` | `DENY_USERNAME_PASSWORD_ENABLED: DISABLED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: REQUIRED`<br>`REGISTRATION_FORM_ENABLED: REQUIRED`<br>`OTP_ENABLED: true`<br>`WEBAUTHN_ENABLED: false`<br>`X509_MFA_ENABLED: false` |
| X509 with Social (IDP) | `SOCIAL_LOGIN_ENABLED: true`<br>`X509_LOGIN_ENABLED: true`<br>`USERNAME_PASSWORD_AUTH_ENABLED: false`<br>`REGISTER_BUTTON_ENABLED: true`| `DENY_USERNAME_PASSWORD_ENABLED: REQUIRED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: DISABLED`<br>`REGISTRATION_FORM_ENABLED: REQUIRED`<br>`OTP_ENABLED: false`<br>`WEBAUTHN_ENABLED: false`<br>`X509_MFA_ENABLED: false` |
| X509 MFA with OTP and WebAuthn | `SOCIAL_LOGIN_ENABLED: false`<br>`X509_LOGIN_ENABLED: true`<br>`USERNAME_PASSWORD_AUTH_ENABLED: false`<br>`REGISTER_BUTTON_ENABLED: true` | `DENY_USERNAME_PASSWORD_ENABLED: REQUIRED`<br>`RESET_CREDENTIAL_FLOW_ENABLED: DISABLED`<br>`REGISTRATION_FORM_ENABLED: REQUIRED`<br>`OTP_ENABLED: true`<br>`WEBAUTHN_ENABLED: true`<br>`X509_MFA_ENABLED: true` |
