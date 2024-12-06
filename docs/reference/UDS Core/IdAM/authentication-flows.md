---
title: Authentication Flow Customization
description: This documentation demonstrates how to customize the uds-core Identity's (Keycloak) Authentication flows
---

# Authentication Flow Customization

## Theme Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [enableSocialLogin]() | Control whether Social Login block is included on the login page. | `true`(default), `false`|
| [enableX509Login]() | Control whether X509 ( CAC ) Login block is included on the login and registration pages. | `true`(default), `false`|
| [enableUsernamePasswordAuth]() | Control whether Username Password Login block is included on the login and registration pages. | `true`(default), `false`|
| [enableRegisterButton]() | Control whether the register button is included on the login page. | `true`(default), `false`|
| [enableRegistrationFields]() | Control whether the additional registration fields are included. Fields: Affiliation, Pay Grade, Organization. | `true`(default), `false`|

## Realm Configuration Definitions
| Setting | Description | Options |
| - | - | - |
| [deny_username_password]() | Control a [`Deny Access`]() flow in the [`MFA Login`]() flow that determines if Username Password can be used to login. | `REQUIRED`, `DISABLED`(default) |
| [reset_credential_flow]() | Control whether a the Reset Credential Auth Flow can be reached by user to reset or set their password. | `REQUIRED`(default), `DISABLED` |
| [registration_form]() | Control whether the registration form can be reached for a new registration. | `REQUIRED`(default), `DISABLED` |
| [update_password_enabled]() | Control whether Users can update their password. Disabling this removes the option for a user to update or setup a password in an environment where passwords are disabled. | `true`(default), `false` |
| [otp_enabled]() | Control whether One Time Password is allowed. | `true`(default), `false` |

## Common Configurations

At this time, UDS Core supports three different avenues of authentication for users. This means three different ways to register and/or login. Below are common Authentication configurations.

### Default Configuration
By defualt UDS Core has all three options configured out of the box.

### Other Configurations

| Authentication Configuration Description | Theme Configurations | Realm Configurations |
| - | - | - |
| Username Password Only | `enableSocialLogin=true`,<br>`enableX509Login=false`,<br>`enableUsernamePasswordAuth=false`,<br>`enableRegisterButton=false`,<br>`enableRegistrationFields=false` | `deny_username_password=DISABLED`,<br>`reset_credential_flow=REQUIRED`,<br>`update_password_enabled=true`,<br>`registration_form=REQUIRED`,<br>`otp_enabled=true` |
| Social (IDP) Only | `enableSocialLogin=true`,<br>`enableX509Login=false`,<br>`enableUsernamePasswordAuth=false`,<br>`enableRegisterButton=false`,<br>`enableRegistrationFields=false` | `deny_username_password=REQUIRED`,<br>`reset_credential_flow=DISABLED`,<br>`update_password_enabled=false`,<br>`registration_form=DISABLED`,<br>`otp_enabled=false` |
| X509 Only | `enableSocialLogin=false`,<br>`enableX509Login=true`,<br>`enableUsernamePasswordAuth=false`,<br>`enableRegisterButton=true`,<br>`enableRegistrationFields=true` | `deny_username_password=REQUIRED`,<br>`reset_credential_flow=DISABLED`,<br>`update_password_enabled=false`,<br>`registration_form=REQUIRED`,<br>`otp_enabled=false` |
| Username Password with X509 | `enableSocialLogin=false`,<br>`enableX509Login=true`,<br>`enableUsernamePasswordAuth=true`,<br>`enableRegisterButton=true`,<br>`enableRegistrationFields=true` | `deny_username_password=DISABLED`,<br>`reset_credential_flow=REQUIRED`,<br>`update_password_enabled=true`,<br>`registration_form=REQUIRED`,<br>`otp_enabled=true` |
| Username Password with Social (IDP) | `enableSocialLogin=true`,<br>`enableX509Login=false`,<br>`enableUsernamePasswordAuth=true`,<br>`enableRegisterButton=true`,<br>`enableRegistrationFields=true` | `deny_username_password=DISABLED`,<br>`reset_credential_flow=REQUIRED`,<br>`update_password_enabled=true`,<br>`registration_form=REQUIRED`,<br>`otp_enabled=true` |
| X509 with Social (IDP) | `enableSocialLogin=true`,<br>`enableX509Login=true`,<br>`enableUsernamePasswordAuth=false`,<br>`enableRegisterButton=true`,<br>`enableRegistrationFields=true `| `deny_username_password=REQUIRED`,<br>`reset_credential_flow=DISABLED`,<br>`update_password_enabled=false`,<br>`registration_form=REQUIRED`,<br>`otp_enabled=false` |




### Username Password Only Authentication
Theme configurations:
```bash
enableSocialLogin=false
enableX509Login=false
enableUsernamePasswordAuth=true
enableRegisterButton=true
enableRegistrationFields=true # optional
```

Realm configurations:
```bash
deny_username_password=DISABLED
reset_credential_flow=REQUIRED
update_password_enabled=true
registration_form=REQUIRED
otp_enabled=true
```

### Social (IDP) Only Authentication
Theme configurations:
```bash
enableSocialLogin=true
enableX509Login=false
enableUsernamePasswordAuth=false
enableRegisterButton=false
enableRegistrationFields=false
```

Realm configurations:
```bash
deny_username_password=REQUIRED
reset_credential_flow=DISABLED
update_password_enabled=false
registration_form=DISABLED
otp_enabled=false
```

### X509 Only Authentication
Theme configurations:
```bash
enableSocialLogin=false
enableX509Login=true
enableUsernamePasswordAuth=false
enableRegisterButton=true
enableRegistrationFields=true # optional
```

Realm configurations:
```bash
deny_username_password=REQUIRED
reset_credential_flow=DISABLED
update_password_enabled=false
registration_form=REQUIRED
otp_enabled=false
```

### Username Password with X509 Authentication
Theme configurations:
```bash
enableSocialLogin=false
enableX509Login=true
enableUsernamePasswordAuth=true
enableRegisterButton=true
enableRegistrationFields=true # optional
```

Realm configurations:
```bash
deny_username_password=DISABLED
reset_credential_flow=REQUIRED
update_password_enabled=true
registration_form=REQUIRED
otp_enabled=true
```

### Username Password with Social (IDP) Authentication
Theme configurations:
```bash
enableSocialLogin=true
enableX509Login=false
enableUsernamePasswordAuth=true
enableRegisterButton=true
enableRegistrationFields=true # optional
```

Realm configurations:
```bash
deny_username_password=DISABLED
reset_credential_flow=REQUIRED
update_password_enabled=true
registration_form=REQUIRED
otp_enabled=true
```

### X509 with Social (IDP) Authentication
Theme configurations:
```bash
enableSocialLogin=true
enableX509Login=true
enableUsernamePasswordAuth=false
enableRegisterButton=true
enableRegistrationFields=true # optional
```

Realm configurations:
```bash
deny_username_password=REQUIRED
reset_credential_flow=DISABLED
update_password_enabled=false
registration_form=REQUIRED
otp_enabled=false
```


### Security Concerns and Misconfigurations

* If Username/Password registration/login is disabled, there is still potential for someone to reach the reset credential flow and set their password. If not configured correctly that user could use that password to authenticate.

* If Username/Password registration/login is disabled but `updated_password_enabled=true`, a user can set their password and potentially use that password to authenticate.

* `registration_form=DISABLED` and `enableRegisterButton=false` are **only** for when Social login ( X509/CAC/PIV/YUBIKEY ) is the only method to register/login


## Known issues
* when x509 is disabled there is still a pop up when a user has one on login page, adding a check that x509 login is enabled still results in something showing up but cant figure out where that comes from

* remove registration fields from account? would need to add something in the realm.json into the big long string that makes it so only admin can view/edit or would have to remove those fields from that string somehow