---
title: Image Customizations
tableOfContents:
  maxHeadingLevel: 5
---

## Add additional jars

Adding additional jars to Keycloak's deployment is as simple as adding that jar to the [src/extra-jars directory](https://github.com/defenseunicorns/uds-identity-config/tree/main/src/extra-jars).

Adding new jars will require building a new identity-config image for [uds-core](https://github.com/defenseunicorns/uds-core).

See [Testing custom image in UDS Core](https://uds.defenseunicorns.com/reference/uds-core/idam/testing-deployment-customizations/) for building, publishing, and using the new image with `uds-core`.

Once `uds-core` has sucessfully deployed with your new image, viewing the Keycloak pod can provide insight into a successful deployment or not. Also describing the Keycloak pod, should display your new image being pulled instead of the default image defined [here](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10) in the events section.

### Branding customizations

The UDS Identity Config supports a limited and opinionated set of branding customizations. This includes:

* Changing the logo
* Changing the footer image
* Changing the favicon
* Changing the background image

These customizations require overriding the Keycloak Helm Chart provided by the UDS Core. Here's an example:

```yaml
packages:
  - name: core
    repository: oci://ghcr.io/defenseunicorns/packages/uds/core
    ref: x.x.x
    overrides:
      keycloak:
        keycloak:
          values:
            - path: themeCustomizations
              value:
                 resources:
                    images:
                       - name: background.jpg
                         configmap:
                            name: keycloak-theme-overrides
                       - name: logo.svg
                         configmap:
                            name: keycloak-theme-overrides
                       - name: footer.png
                         configmap:
                            name: keycloak-theme-overrides
                       - name: favicon.svg
                         configmap:
                            name: keycloak-theme-overrides
```

The configuration supports only four potential keys: `background.jpg`, `logo.svg`, `footer.png`, and `favicon.svg` which are expected to exist in the corresponding ConfigMaps. In this example, all four images reside in the same ConfigMap named `keycloak-theme-overrides`. The values of these keys are base64 encoded images hosted as `binaryData` part of the ConfigMap. You can create it using the following command:

```bash
kubectl create configmap keycloak-theme-overrides \
  --from-file=background.jpg=path/to/local/directory/background.jpg \
  --from-file=logo.svg=path/to/local/directory/logo.svg \
  --from-file=footer.png=path/to/local/directory/footer.png \
  --from-file=favicon.svg=path/to/local/directory/favicon.svg
```

## Customizing Theme

**Official Theming Docs**

* [Official Keycloak Theme Docs](https://www.keycloak.org/docs/latest/server_development/#_themes)
* [Official Keycloak Theme Github](https://github.com/keycloak/keycloak/tree/b066c59a83c99d757d501d8f5e6061372706d24d/themes/src/main/resources/theme)

For other changes beyond these images you will need to build a custom theme and identity-config image. Changes can be made to the [src/theme](https://github.com/defenseunicorns/uds-identity-config/tree/main/src/theme) directory. At this time only Account and Login themes are included, but email, admin, and welcome themes could be added as well.

### Registration Form Fields

Registration Form Fields, which by default are enabled, can be disabled to minimize the steps to register a new user. See [this section](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/#templated-realm-values) for the example of disabling the registration form fields with the `themeCustomizations.settings.enableRegistrationFields` environment variable.

When disabled, the following fields will not be present during registration:
- Affiliation
- Pay Grade
- Unit, Organization or Company Name

### Testing Changes

To test the `identity-config` theme changes, a local running Keycloak instance is required.

Don't have a local Keycloak instance? The simplest testing path is utilizing [uds-core](https://github.com/defenseunicorns/uds-core), specifically the `dev-identity` task. This will create a k3d cluster with Istio, Pepr, Keycloak, and Authservice.

Once that cluster is up and healthy and after making theme changes, utilize this task to :

1. Execute this command:

   ```bash
      uds run dev-theme
   ```

2. View the changes in the browser

## Customizing Realm

The `UDS Identity` realm is defined in the realm.json found in [src/realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json). This can be modified and will require a new `uds-identity-config` image for `uds-core`.

:::note
Be aware that changing values in the realm may also need to be updated throughout the configuration of Keycloak and Authservice in `uds-core`. For example, changing the realm name will break a few different things within Keycloak unless those values are changed in `uds-core` as well.
:::

See the [Testing custom image in UDS Core](https://uds.defenseunicorns.com/reference/uds-core/idam/testing-deployment-customizations/) for building, publishing, and using the new image with `uds-core`.

### Templated Realm Values

Keycloak supports using environment variables within the realm configuration, see [docs](https://www.keycloak.org/server/importExport).

These environment variables have default values set in the realm.json that uses the following syntax:

```yaml
  ${REALM_GOOGLE_IDP_ENABLED:false}
```

In the uds-core keycloak [values.yaml](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml), the `realmInitEnv` defines set of environment variables that can be used to configure the realm different from default values.

These environment variables will be created with a prefix `REALM_` to avoid collisions with keycloak environment variables. If necessary to add additional template variables within the realm.json must be prefixed with `REALM_`.

For example, this bundle override contains all the available overrides:

```yaml
overrides:
   keycloak:
      keycloak:
         values:
            path: realmInitEnv
            value:
               GOOGLE_IDP_ENABLED: true
               GOOGLE_IDP_ID: <fill in value here>
               GOOGLE_IDP_SIGNING_CERT: <fill in value here>
               GOOGLE_IDP_NAME_ID_FORMAT: <fill in value here>
               GOOGLE_IDP_CORE_ENTITY_ID: <fill in value here>
               GOOGLE_IDP_ADMIN_GROUP: <fill in value here>
               GOOGLE_IDP_AUDITOR_GROUP: <fill in value here>
               EMAIL_VERIFICATION_ENABLED: true
               TERMS_AND_CONDITIONS_ENABLED: true
               PASSWORD_POLICY: <fill in value here>
               X509_OCSP_FAIL_OPEN: true
               ACCESS_TOKEN_LIFESPAN: 600
               SSO_SESSION_LIFESPAN_TIMEOUT: 1200
               SSO_SESSION_MAX_LIFESPAN: 36000
            path: realmAuthFlows
            value:
               USERNAME_PASSWORD_AUTH_ENABLED: true
               X509_AUTH_ENABLED: true
               SOCIAL_AUTH_ENABLED: true
               OTP_ENABLED: true
               WEBAUTHN_ENABLED: true
               X509_MFA_ENABLED: true
            path: themeCustomizations.settings
            value:
               enableRegistrationFields: true
```

> These environment variables can be found in the [realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json).

:::note
**Important**: By allowing certificates to pass when no revocation check is performed, you accept the **risk** of potentially allowing revoked certificates to authenticate. This can pose a significant security threat depending on your organization’s compliance requirements and threat model.
- **Fail-Closed (`X509_OCSP_FAIL_OPEN:false`)**: More secure (no unchecked certificates) but can disrupt logins if the OCSP responder is unreachable.
- **Fail-Open (`X509_OCSP_FAIL_OPEN:true`)**: More forgiving (users still log in if checks fail) but can allow revoked certificates if the OCSP server is down.
:::

### Customizing Session and Access Token Timeouts
The `SSO_SESSION_IDLE_TIMEOUT` specifies how long a session remains active without user activity, while the `ACCESS_TOKEN_LIFESPAN` defines the validity duration of an access token before it requires refreshing. The `SSO_SESSION_MAX_LIFESPAN` determines the maximum duration a session can remain active, regardless of user activity.

To ensure smooth session management, configure the idle timeout to be longer than the access token lifespan (e.g., 10 minutes idle, 5 minutes lifespan) so tokens can be refreshed before the session expires, and ensure the max lifespan is set appropriately (e.g., 8 hours) to enforce session limits. Misalignment, such as setting a longer token lifespan than the idle timeout or not aligning the max lifespan with session requirements, can result in sessions ending unexpectedly or persisting longer than intended.