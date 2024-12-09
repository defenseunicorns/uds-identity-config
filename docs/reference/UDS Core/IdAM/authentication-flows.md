---
title: Authentication Flow Customization
tableOfContents:
  maxHeadingLevel: 5
---

# Authentication Flow Customization

:::note Environment variables configured in the [uds-core Keycloak values.yaml file](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L28-L40) have `REALM_` [appended to them during creation.](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/#templated-realm-values). See [official Customization docs](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/) for more information. :::

## Theme Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [ENABLE_SOCIAL_LOGIN](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L41) | Control whether Social Login block is included on the login page. | `true`(default), `false`|
| [ENABLE_X509_LOGIN](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L42) | Control whether X509 ( CAC ) Login block is included on the login and registration pages. | `true`(default), `false`|
| [ENABLE_USERNAME_PASSWORD_AUTH](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L43) | Control whether Username Password Login block is included on the login and registration pages. This will also control the realm configuration for updating passwords or setting a new password from users account management. | `true`(default), `false`|
| [ENABLE_REGISTER_BUTTON](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L44) | Control whether the register button is included on the login page. | `true`(default), `false`|
| [ENABLE_REGISTRATION_FIELDS](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L45) | Control whether the additional registration fields are included. Fields: Affiliation, Pay Grade, Organization. | `true`(default), `false`|

## Realm Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [deny_username_password](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L46) | Control a [`Deny Access`](https://github.com/defenseunicorns/uds-identity-config/blob/rework-auth-flows/src/realm.json#L2259-L2266) flow in the [`MFA Login`](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json#L2243-L2276) flow that determines if Username Password can be used to login. | `REQUIRED`, `DISABLED`(default) |
| [reset_credential_flow](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L47) | Control whether a the Reset Credential Auth Flow can be reached by user to reset or set their password. | `REQUIRED`(default), `DISABLED` |
| [registration_form](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L48) | Control whether the registration form can be reached for a new registration. | `REQUIRED`(default), `DISABLED` |
| [ENABLE_USERNAME_PASSWORD_AUTH](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L43) | Control whether Users can update their password. Disabling this removes the option for a user to update or setup a password in an environment where passwords are disabled. | `true`(default), `false` |
| [otp_enabled](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L49) | Control whether One Time Password is allowed. | `true`(default), `false` |

## Common Configurations

At this time, UDS Core supports three different avenues of authentication for users. This means three different ways to register and/or login. Below are common Authentication configurations.

### Default Configuration
By defualt UDS Core has all three options configured out of the box.

### Other Configurations

| Authentication Configuration Description | Theme Configurations | Realm Configurations |
| - | - | - |
| Username Password Only | `ENABLE_SOCIAL_LOGIN: false`<br>`ENABLE_X509_LOGIN: false`<br>`ENABLE_USERNAME_PASSWORD_AUTH: true`<br>`ENABLE_REGISTER_BUTTON: true`<br>`ENABLE_REGISTRATION_FIELDS: true` | `deny_username_password: DISABLED`<br>`reset_credential_flow: REQUIRED`<br>`registration_form: REQUIRED`<br>`otp_enabled: true` |
| Social (IDP) Only | `ENABLE_SOCIAL_LOGIN: true`<br>`ENABLE_X509_LOGIN: false`<br>`ENABLE_USERNAME_PASSWORD_AUTH: false`<br>`ENABLE_REGISTER_BUTTON: false`<br>`ENABLE_REGISTRATION_FIELDS: false` | `deny_username_password: REQUIRED`<br>`reset_credential_flow: DISABLED`<br>`registration_form: DISABLED`<br>`otp_enabled: false` |
| X509 Only | `ENABLE_SOCIAL_LOGIN: false`<br>`ENABLE_X509_LOGIN: true`<br>`ENABLE_USERNAME_PASSWORD_AUTH: false`<br>`ENABLE_REGISTER_BUTTON: true`<br>`ENABLE_REGISTRATION_FIELDS: true` | `deny_username_password: REQUIRED`<br>`reset_credential_flow: DISABLED`<br>`registration_form: REQUIRED`<br>`otp_enabled: false` |
| Username Password with X509 | `ENABLE_SOCIAL_LOGIN: false`<br>`ENABLE_X509_LOGIN: true`<br>`ENABLE_USERNAME_PASSWORD_AUTH: true`<br>`ENABLE_REGISTER_BUTTON: true`<br>`ENABLE_REGISTRATION_FIELDS: true` | `deny_username_password: DISABLED`<br>`reset_credential_flow: REQUIRED`<br>`registration_form: REQUIRED`<br>`otp_enabled: true` |
| Username Password with Social (IDP) | `ENABLE_SOCIAL_LOGIN: true`<br>`ENABLE_X509_LOGIN: false`<br>`ENABLE_USERNAME_PASSWORD_AUTH: true`<br>`ENABLE_REGISTER_BUTTON: true`<br>`ENABLE_REGISTRATION_FIELDS: true` | `deny_username_password: DISABLED`<br>`reset_credential_flow: REQUIRED`<br>`registration_form: REQUIRED`<br>`otp_enabled: true` |
| X509 with Social (IDP) | `ENABLE_SOCIAL_LOGIN: true`<br>`ENABLE_X509_LOGIN: true`<br>`ENABLE_USERNAME_PASSWORD_AUTH: false`<br>`ENABLE_REGISTER_BUTTON: true`<br>`ENABLE_REGISTRATION_FIELDS: true `| `deny_username_password: REQUIRED`<br>`reset_credential_flow: DISABLED`<br>`registration_form: REQUIRED`<br>`otp_enabled: false` |

### Username Password Only Authentication
Theme configurations:
```bash
ENABLE_SOCIAL_LOGIN: false
ENABLE_X509_LOGIN: false
ENABLE_USERNAME_PASSWORD_AUTH: true
ENABLE_REGISTER_BUTTON: true
ENABLE_REGISTRATION_FIELDS: true # optional
```

Realm configurations:
```bash
deny_username_password: DISABLED
reset_credential_flow: REQUIRED
registration_form: REQUIRED
otp_enabled: true
```

### Social (IDP) Only Authentication
Theme configurations:
```bash
ENABLE_SOCIAL_LOGIN: true
ENABLE_X509_LOGIN: false
ENABLE_USERNAME_PASSWORD_AUTH: false
ENABLE_REGISTER_BUTTON: false
ENABLE_REGISTRATION_FIELDS: false
```

Realm configurations:
```bash
deny_username_password: REQUIRED
reset_credential_flow: DISABLED
registration_form: DISABLED
otp_enabled: false
```

### X509 Only Authentication
Theme configurations:
```bash
ENABLE_SOCIAL_LOGIN: false
ENABLE_X509_LOGIN: true
ENABLE_USERNAME_PASSWORD_AUTH: false
ENABLE_REGISTER_BUTTON: true
ENABLE_REGISTRATION_FIELDS: true # optional
```

Realm configurations:
```bash
deny_username_password: REQUIRED
reset_credential_flow: DISABLED
registration_form: REQUIRED
otp_enabled: false
```

### Username Password with X509 Authentication
Theme configurations:
```bash
ENABLE_SOCIAL_LOGIN: false
ENABLE_X509_LOGIN: true
ENABLE_USERNAME_PASSWORD_AUTH: true
ENABLE_REGISTER_BUTTON: true
ENABLE_REGISTRATION_FIELDS: true # optional
```

Realm configurations:
```bash
deny_username_password: DISABLED
reset_credential_flow: REQUIRED
registration_form: REQUIRED
otp_enabled: true
```

### Username Password with Social (IDP) Authentication
Theme configurations:
```bash
ENABLE_SOCIAL_LOGIN: true
ENABLE_X509_LOGIN: false
ENABLE_USERNAME_PASSWORD_AUTH: true
ENABLE_REGISTER_BUTTON: true
ENABLE_REGISTRATION_FIELDS: true # optional
```

Realm configurations:
```bash
deny_username_password: DISABLED
reset_credential_flow: REQUIRED
registration_form: REQUIRED
otp_enabled: true
```

### X509 with Social (IDP) Authentication
Theme configurations:
```bash
ENABLE_SOCIAL_LOGIN: true
ENABLE_X509_LOGIN: true
ENABLE_USERNAME_PASSWORD_AUTH: false
ENABLE_REGISTER_BUTTON: true
ENABLE_REGISTRATION_FIELDS: true # optional
```

Realm configurations:
```bash
deny_username_password: REQUIRED
reset_credential_flow: DISABLED
registration_form: REQUIRED
otp_enabled: false
```


### Security Concerns and Misconfigurations

* If Username/Password registration/login is disabled, there is still potential for someone to reach the reset credential flow and set their password. If not configured correctly that user could use that password to authenticate.

* If Username/Password registration/login is disabled but `updated_password_enabled=true`, a user can set their password and potentially use that password to authenticate.

* `registration_form=DISABLED` and `ENABLE_REGISTER_BUTTON=false` are **only** for when Social login ( X509/CAC/PIV/YUBIKEY ) is the **only** method to register/login.


## Known issues
* when x509 is disabled there is still a pop up when a user has one on login page, adding a check that x509 login is enabled still results in something showing up but cant figure out where that comes from

* remove registration fields from account? would need to add something in the realm.json into the big long string that makes it so only admin can view/edit or would have to remove those fields from that string somehow