---
title: Authentication Flow Customization
tableOfContents:
  maxHeadingLevel: 5
---

## Authentication Flow Options

UDS Core comes equipped with a robust authentication framework that supports multiple authentication methods to meet diverse security requirements and user preferences. Here’s a breakdown of the authentication options available:

---

1. Username and Password

    The most traditional form of authentication involves users providing a username and password that must match the credentials stored in the system. This method is widely used due to its simplicity and direct control over access credentials.

    ---

2. SSO (Single Sign-On)

    Single Sign-On (SSO) allows users to authenticate with one set of credentials to access multiple applications. UDS Core can be configured to integrate with various SSO providers, such as Google SSO, Microsoft Entra, and others, streamlining the login process and reducing the burden of managing multiple usernames and passwords.

    ---

3. x509 Certificate

    x509 certificates provide a way to authenticate using digital certificates. It is commonly used in environments that require higher security, such as corporate or governmental networks. This method uses public key infrastructure (PKI) to verify the user's identity through a trusted certificate authority.

    > **Air-Gapped Note**: In environments without reliable internet access, **OCSP revocation checks** may fail if the designated OCSP responder cannot be reached. As a short-term workaround, you can configure “fail-open” or disable OCSP checks entirely. However, these approaches carry **security risks** (e.g., potentially allowing revoked certificates).

---

![Authentication Flow Options](https://github.com/defenseunicorns/uds-identity-config/blob/main/docs/.images/diagrams/uds-core-auth-flows-options.svg?raw=true)

## MFA Authentication

UDS Core comes with two different options for MFA requirements. One time password (OTP) and WebAuthn options can be configured. These options are available for both `Username and Password` and `x509` authentication flows. They are controlled individually, to provide the most amount of configurability.

```yaml
    - path: realmAuthFlows
        value:
            USERNAME_PASSWORD_AUTH_ENABLED: true
            X509_AUTH_ENABLED: true
            SOCIAL_AUTH_ENABLED: true
            OTP_ENABLED: true
            WEBAUTHN_ENABLED: false
            X509_MFA_ENABLED: false
```

Above is the complete list of authentication configurations from a bundle override. Below is the description of what each of those do:

| Name | Description | Default Value |
| - | - | - |
| `USERNAME_PASSWORD_AUTH_ENABLED` | Controls whether the `Username and Password` authentication flow is allowed and present on the login page. |  `true`(default), `false` |
| `X509_AUTH_ENABLED` | Controls whether the `X509` authentication is allowed and present (when a proper certificate is present) on the login page. | `true`(default), `false` |
| `SOCIAL_AUTH_ENABLED` | Controls whether the `Social` authentication is allowed and present on the login page. This requires that an Identity Provider be configured as well. | `true`(default), `false` |
| `OTP_ENABLED` | Control whether `OTP` MFA is enabled, making it required for `Username and Password` authentication. | `true`(default), `false` |
| `WEBAUTHN_ENABLED` | Control whether `WebAuthn` MFA is enabled, making it required for `Username and Password` authentication. | `true`, `false`(default) |
| `X509_MFA_ENABLED` | Control whether `X509` authentication flow should also include MFA. Enabling this requires `OTP_ENABLED` or `WEBAUTHN_ENABLED` as well. | `true`, `false`(default) |

:::warning
We shift all authn and authz responsibilies to the Identity Provider if choosing to use SSO, this means that MFA is not configurable for SSO options.
:::

## Authentication Flows in UDS Core

UDS Core is shipped with a basic authentication flow that includes all three options out of the box. The following diagram shows the basic authentication flows that are deployed with standard UDS Core:

![UDS Core Authentication Flow](https://github.com/defenseunicorns/uds-identity-config/blob/main/docs/.images/diagrams/uds-core-auth-flows-basic.svg?raw=true)

### Customizing Authentication Flows

Different operational environments may necessitate distinct authentication flows to comply with specific security policies, regulatory demands, or demographic requirements. UDS Core facilitates the customization of these flows, allowing for tailored security measures and user interfaces. The diagram below illustrates various combinations of the three authentication methods that can be adapted to meet unique operational needs:

![Complex Authentication Flows](https://github.com/defenseunicorns/uds-identity-config/blob/main/docs/.images/diagrams/uds-core-auth-flows-complex.svg?raw=true)

These customizations not only ensure appropriate security configurations by enabling or disabling specific flows but also maintain a seamless user experience by adjusting the Keycloak theme accordingly.

The following sections provide a step-by-step guide on how to customize UDS Core to deploy specific authentication flows, catering to the particular needs and guidelines of your environment.

## Authentication Flow Customization

:::note
Environment variables configured in the [uds-core Keycloak values.yaml file](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L30-32) have `REALM_` appended to them during creation. See [Customization docs](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/) for more information.
:::

:::warning
If upgrading uds-core, be aware that Keycloak Admin manual configuration will probably be required to set new Realm values. See the manual configuration section below for how to do this.
:::

### Bundle Overrides
To simplify the configuration of the available authentication flows, the following three environment variables have been exposed. These variables default to `true` in UDS Core, override their values in a bundle to disable.

:::note
These settings allow for enabling/disabling one or more of the Auth flows. Be aware that disabling all three will result in no options for registration and authentication (login).
:::

| Setting | Description | Options |
| - | - | - |
| [USERNAME_PASSWORD_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L30) | Toggle on/off the Username and Password Authentication flow. When disabled there will be no username password login, password / password confirm registration fields, no credential reset, and no update password options available. | `true`(default), `false` |
| [X509_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L31) | Toggle on/off X509 (CAC) Authentication flow. | `true`(default), `false` |
| [SOCIAL_AUTH_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L32) | Toggle on/off Social (Google SSO, Azure AD, etc. ) Authentication flows.| `true`(default), `false` |
| [X509_MFA_ENABLED](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L32) | Toggle on/off X509 to require additional MFA options (OTP, WebAuthn, etc).| `true`, `false`(default) |

These three variables handle the complexities of configuring the following environment variables, which are responsible for both visual (theme) and security (realm). The following variables are not exposed for overriding.

### Manual Configuration

#### Theme Configurations
Theme's cannot be clickops'ed, for these changes to take affect an upgrade or fresh deployment will be required. Another option is exec-ing into the the keycloak pod and copying in the new themes to the `/opt/keycloak/theme/themes/login/` directory. After copying in the theme changes, the theme changes depend on environment variables being defined in the [theme.properties file](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/theme/login/theme.properties). The above table demonstrates the different environment variables for the `theme.properties` file.

#### Realm Configurations
All Realm Configurations require accesss to the Keycloak admin portal.

| Configuration | How to Configure |
| - | - |
| `DENY_USERNAME_PASSWORD_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `UDS Authentication` Authentication Flow<br> 3. `DISABLE` the `Deny Access` step that is below the `Username Password Form` |
| `RESET_CREDENTIAL_FLOW_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `UDS Reset Credentials` Authentication Flow<br> 3. `DISABLE` the `Reset Password` step |
| `REGISTRATION_FORM_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `UDS Registration` Authentication Flow<br> 3. `DISABLE` the `UDS Registration form` step |
| `OTP_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `Required Action` tab at the top of the Authentication view<br> 3. Toggle off the `Configure OTP` |
| `WEBAUTHN_ENABLED` | 1. Realm Authentication tab<br> 2. Select the `Required Action` tab at the top of the Authentication view<br> 3. Toggle on the `Webauthn Register Passwordless` `Enabled` column<br> 4. Select the `Flows` tab at the top of the Authentication view<br> 5. Select the `UDS Authentication` flow<br> 6. Set the `MFA` sub-flow to `Required`<br> 7. Set the `WebAuthn Passwordless Authenticator` in the `MFA` sub-flow to `Required` |
