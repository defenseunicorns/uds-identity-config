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

---

![Authentication Flow Options](https://github.com/defenseunicorns/uds-identity-config/blob/update-existing-diagrams/docs/.images/diagrams/uds-core-auth-flows-options.svg?raw=true)

## Authentication Flows in UDS Core

UDS Core is shipped with a basic authentication flow that includes all three options out of the box. The following diagram shows the basic authentication flows that are deployed with standard UDS Core:

![UDS Core Authentication Flow](https://github.com/defenseunicorns/uds-identity-config/blob/update-existing-diagrams/docs/.images/diagrams/uds-core-auth-flows-basic.svg?raw=true)

### Customizing Authentication Flows

Different operational environments may necessitate distinct authentication flows to comply with specific security policies, regulatory demands, or demographic requirements. UDS Core facilitates the customization of these flows, allowing for tailored security measures and user interfaces. The diagram below illustrates various combinations of the three authentication methods that can be adapted to meet unique operational needs:

![Complex Authentication Flows](https://github.com/defenseunicorns/uds-identity-config/blob/update-existing-diagrams/docs/.images/diagrams/uds-core-auth-flows-complex.svg?raw=true)

These customizations not only ensure appropriate security configurations by enabling or disabling specific flows but also maintain a seamless user experience by adjusting the Keycloak theme accordingly.

The following sections provide a step-by-step guide on how to customize UDS Core to deploy specific authentication flows, catering to the particular needs and guidelines of your environment.

## Authentication Flow Customization

:::note
Environment variables configured in the [uds-core Keycloak values.yaml file](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L30-32) have `REALM_` appended to them during creation. See [ Customization docs](https://uds.defenseunicorns.com/reference/uds-core/idam/image-customizations/) for more information.
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
