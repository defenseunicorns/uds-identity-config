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

## Customizing Theme

**Official Theming Docs**

* [Official Keycloak Theme Docs](https://www.keycloak.org/docs/latest/server_development/#_themes)
* [Official Keycloak Theme Github](https://github.com/keycloak/keycloak/tree/b066c59a83c99d757d501d8f5e6061372706d24d/themes/src/main/resources/theme)

For other changes beyond these images you will need to build a custom theme and identity-config image. Changes can be made to the [src/theme](https://github.com/defenseunicorns/uds-identity-config/tree/main/src/theme) directory. At this time only Account and Login themes are included, but email, admin, and welcome themes could be added as well.

### Branding Customizations

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
                    - name: background.png
                      configmap:
                       name: keycloak-theme-overrides
                    - name: logo.png
                      configmap:
                        name: keycloak-theme-overrides
                    - name: footer.png
                      configmap:
                        name: keycloak-theme-overrides
                    - name: favicon.png
                      configmap:
                        name: keycloak-theme-overrides
```

The configuration supports four keys for different images: `background.png`, `logo.png`, `footer.png`, and `favicon.png`. You can set any or all of these images (you do not have to override all of them), and the corresponding key(s) must exist in your designated ConfigMap(s). Note that you must pre-create this ConfigMap in the `keycloak` namespace before deploying/upgrading Core with these overrides. In the above example all images are in the same ConfigMap named `keycloak-theme-overrides`. An easy way to generate the ConfigMap manifest is using the following command (including whichever images you need and specifying the correct paths to your local images):

```bash
kubectl create configmap keycloak-theme-overrides \
  --from-file=background.png=path/to/local/directory/background.png \
  --from-file=logo.png=path/to/local/directory/logo.png \
  --from-file=footer.png=path/to/local/directory/footer.png \
  --from-file=favicon.png=path/to/local/directory/favicon.png \
  -n keycloak --dry-run=client -o=yaml > theme-image-cm.yaml
```

To deploy this it is easiest to make a small zarf package referencing the manifest you just created:

```yaml
kind: ZarfPackageConfig
metadata:
  name: keycloak-theme-overrides
  version: 0.1.0

components:
  - name: keycloak-theme-overrides
    required: true
    manifests:
      - name: configmap
        namespace: keycloak # Ensure this is in the Keycloak namespace
        files:
          - theme-image-cm.yaml # Update to the manifest you have locally
```

Then create and deploy this zarf package _prior_ to deploying/upgrading UDS Core/Keycloak.

### Terms and Conditions Customizations

In a similar theme to Branding customizations, the UDS Identity Config supports adjusting the Terms and Conditions (if enabled).

These customizations similarly require overriding the Keycloak Helm Chart provided by the UDS Core. Here's an example:

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
                termsAndConditions:
                  text:
                    configmap:
                      key: text
                      name: keycloak-theme-overrides
```

The configuration supports a single key (named however you want) which must exist in the corresponding ConfigMap(s). Note that you must pre-create this ConfigMap in the `keycloak` namespace before deploying/upgrading Core with these overrides. The contents of this key must be your custom Terms and Conditions content, formatted as a single line HTML string. As a basic example you might want some terms and conditions like the following HTML:

```html
<h4>By logging in you agree to the following:</h4>
<ul>
  <li>Terms</li>
  <li>And</li>
  <li>Conditions</li>
</ul>
```

In order to properly format this you will need to replace any newlines with the literal newline character (`\n`), converting your HTML to a single line. Using the above example that would look like this (again note the use of `\n` in place of the newlines):

```
<h4>By logging in you agree to the following:</h4>\n<ul>\n<li>Terms</li>\n<li>And</li>\n<li>Conditions</li>\n</ul>
```

:::tip
This replacement process can easily be done with a tool like `sed`:
```bash
# This will require GNU sed
cat terms.html | sed ':a;N;$!ba;s/\n/\\n/g' > single-line.html
```
:::

Your new single-line HTML file can be used to generate a properly formatted ConfigMap with the following command:

```bash
kubectl create configmap keycloak-theme-overrides \
  --from-file=text=path/to/local/directory/single-line.html \
  -n keycloak --dry-run=client -o=yaml > terms-and-confitions-cm.yaml
```

To deploy this it is easiest to make a small zarf package referencing the manifest you just created:

```yaml
kind: ZarfPackageConfig
metadata:
  name: keycloak-terms-and-conditions
  version: 0.1.0

components:
  - name: keycloak-terms-and-conditions
    required: true
    manifests:
      - name: configmap
        namespace: keycloak # Ensure this is in the Keycloak namespace
        files:
          - terms-and-confitions-cm.yaml # Update to the manifest you have locally
```

Then create and deploy this zarf package _prior_ to deploying/upgrading UDS Core/Keycloak.

:::tip
In order to speed up the development process of the Customized Terms and Conditions, you can edit the ConfigMap in your cluster and then cycle the Keycloak pod to reload your updated Terms and Conditions. This will allow you to see the changes quicker without needing to rebuild/redeploy each time.
:::

:::note
The default terms and conditions provided are based on the standard DoD Notice and Consent Banner. The source HTML for these terms is in the identity-config repository [here](hhttps://github.com/defenseunicorns/uds-identity-config/blob/v0.15.0/src/theme/login/terms.ftl#L25-L79) and could be used as a starting point for customizing. Note that you will need to follow the above steps to properly format this as single-line HTML and create a ConfigMap with its contents. There are some limitations and you won't be able to dynamically lookup resources (i.e. `${url.resourcesPath}`) due to the way this input is dynamically injected into the terms template so keep this in mind if trying to reference external images.
:::

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

The `UDS Identity` realm is defined in the realm.json found in [src/realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json5). This can be modified and will require a new `uds-identity-config` image for `uds-core`.

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
        - path: realmInitEnv
          value:
            GOOGLE_IDP_ENABLED: true
            GOOGLE_IDP_ID: <fill in value here>
            GOOGLE_IDP_SIGNING_CERT: <fill in value here>
            GOOGLE_IDP_NAME_ID_FORMAT: <fill in value here>
            GOOGLE_IDP_CORE_ENTITY_ID: <fill in value here>
            GOOGLE_IDP_ADMIN_GROUP: <fill in value here>
            GOOGLE_IDP_AUDITOR_GROUP: <fill in value here>
            EMAIL_AS_USERNAME: true
            EMAIL_VERIFICATION_ENABLED: true
            TERMS_AND_CONDITIONS_ENABLED: true
            PASSWORD_POLICY: <fill in value here>
            X509_OCSP_FAIL_OPEN: true
            ACCESS_TOKEN_LIFESPAN: 600
            SSO_SESSION_LIFESPAN_TIMEOUT: 1200
            SSO_SESSION_MAX_LIFESPAN: 36000
            SSO_SESSION_MAX_PER_USER: 10
            MAX_TEMPORARY_LOCKOUTS: 3
        - path: realmAuthFlows
          value:
            USERNAME_PASSWORD_AUTH_ENABLED: true
            X509_AUTH_ENABLED: true
            SOCIAL_AUTH_ENABLED: true
            OTP_ENABLED: true
            WEBAUTHN_ENABLED: true
            X509_MFA_ENABLED: true
        - path: themeCustomizations.settings
          value:
            enableRegistrationFields: true
        - path: realmConfig.maxInFlightLoginsPerUser
          value: 10
```

> These environment variables can be found in the [realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json5) and the table below summarizes their purpose:

| Option                       | Default value                                                                                                                                                                                        | Purpose                                                               |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| GOOGLE_IDP_*                 | false, unset                                                                                                                                                                                         | Google SAML Identity Provider configuration settings.                 |
| EMAIL_AS_USERNAME            | false                                                                                                                                                                                                | Treat the user's email as their username in the realm.                |
| EMAIL_VERIFICATION_ENABLED   | false                                                                                                                                                                                                | Require users to verify their email address before using the account. |
| TERMS_AND_CONDITIONS_ENABLED | false                                                                                                                                                                                                | Enable the Terms and Conditions screen that users must accept.        |
| PASSWORD_POLICY              | hashAlgorithm(pbkdf2-sha256) and forceExpiredPasswordChange(60) and specialChars(2) and digits(1) and lowerCase(1) and upperCase(1) and passwordHistory(5) and length(15) and notUsername(undefined) | Define the Keycloak password policy applied to users in the realm.    |
| X509_OCSP_FAIL_OPEN          | false                                                                                                                                                                                                | Control OCSP fail behavior for X.509 certificate authentication       |
| ACCESS_TOKEN_LIFESPAN        | 60                                                                                                                                                                                                   | Access token validity period in seconds.                              |
| SSO_SESSION_LIFESPAN_TIMEOUT | 600                                                                                                                                                                                                  | Session idle timeout in seconds.                                      |
| SSO_SESSION_MAX_LIFESPAN     | 36000                                                                                                                                                                                                | Maximum absolute session lifespan in seconds, regardless of activity. |
| SSO_SESSION_MAX_PER_USER     | 0                                                                                                                                                                                                    | Maximum number of concurrent active sessions per user.                |

:::note
**Important**: By allowing certificates to pass when no revocation check is performed, you accept the **risk** of potentially allowing revoked certificates to authenticate. This can pose a significant security threat depending on your organization’s compliance requirements and threat model.
- **Fail-Closed (`X509_OCSP_FAIL_OPEN:false`)**: More secure (no unchecked certificates) but can disrupt logins if the OCSP responder is unreachable.
- **Fail-Open (`X509_OCSP_FAIL_OPEN:true`)**: More forgiving (users still log in if checks fail) but can allow revoked certificates if the OCSP server is down.
:::

Values set in both `realmInitEnv` and `realmAuthFlows` are applied only during the initial import of the `uds` Keycloak Realm. Updating these values at runtime will not affect the running Keycloak instance; to apply changes, you must redeploy the Keycloak package. In contrast, values provided in `themeCustomizations.settings` and `realmConfig` are designed to be updated at runtime and do not require redeployment of the Keycloak package.

### Customizing Session and Access Token Timeouts and limits
The `SSO_SESSION_IDLE_TIMEOUT` specifies how long a session remains active without user activity, while the `ACCESS_TOKEN_LIFESPAN` defines the validity duration of an access token before it requires refreshing. The `SSO_SESSION_MAX_LIFESPAN` determines the maximum duration a session can remain active, regardless of user activity.

To ensure smooth session management, configure the idle timeout to be longer than the access token lifespan (e.g., 10 minutes idle, 5 minutes lifespan) so tokens can be refreshed before the session expires, and ensure the max lifespan is set appropriately (e.g., 8 hours) to enforce session limits. Misalignment, such as setting a longer token lifespan than the idle timeout or not aligning the max lifespan with session requirements, can result in sessions ending unexpectedly or persisting longer than intended.

The `SSO_SESSION_MAX_PER_USER` provides a limit on the number of active sessions a user can use. You can specify 0 to allow unlimited sessions per user, or set a specific number to limit concurrent sessions. This is useful for controlling resource usage and ensuring that users do not have an excessive number of active sessions at once.

### OpenTofu Keycloak Client Configuration

The UDS Identity Config includes a Keycloak client that can be used by OpenTofu to manage Keycloak resources programmatically. This client is disabled by default for security reasons.

:::caution
**Critical Security Requirements**

1. **Pre-Deployment Configuration**
   - **You must configure authentication flows before deploying UDS Core**
   - UDS Core will apply default authentication flows if not configured first
   - This is a critical security step to prevent unauthorized access

2. **Deployment Options**:

   **Option 1: Disable All Flows (Recommended)**
   This approach starts with maximum security by disabling all authentication methods:
   ```yaml
   overrides:
     keycloak:
       keycloak:
         values:
           - path: realmInitEnv
             value:
               OPENTOFU_CLIENT_ENABLED: true
           - path: realmAuthFlows
             value:
               USERNAME_PASSWORD_AUTH_ENABLED: false
               X509_AUTH_ENABLED: false
               SOCIAL_AUTH_ENABLED: false
               OTP_ENABLED: false
               WEBAUTHN_ENABLED: false
               X509_MFA_ENABLED: false
   ```
   This is the most secure approach but requires OpenTofu to enable specific authentication methods after deployment.

   **Option 2: Configure Final Flows Upfront**
   If you know your exact authentication requirements, you can configure them directly. For example, to enable Username/Password + OTP authentication only:
   ```yaml
   overrides:
     keycloak:
       keycloak:
         values:
           - path: realmInitEnv
             value:
               OPENTOFU_CLIENT_ENABLED: true
           - path: realmAuthFlows
             value:
               USERNAME_PASSWORD_AUTH_ENABLED: true
               X509_AUTH_ENABLED: false
               SOCIAL_AUTH_ENABLED: false
               OTP_ENABLED: true
               WEBAUTHN_ENABLED: false
               X509_MFA_ENABLED: false
   ```
   This approach is simpler initially but may require manual steps if your requirements change.

3. **Security Considerations**
   - The `uds-opentofu-client` has elevated permissions - protect its credentials
   - Never modify or delete the `uds-operator` clients as they are critical for system operation
   - Monitor authentication logs after deployment for any unexpected access attempts

4. **Verification**
   - Test authentication in a non-production environment first
   - For detailed information on available authentication flows, see [Authentication Flow Documentation](/reference/uds-core/idam/authentication-flows)
:::

#### OpenTofu Provider Configuration

To use the OpenTofu Keycloak client, you'll need to configure the [Keycloak provider](https://registry.terraform.io/providers/keycloak/keycloak/latest/docs) to use the OpenTofu client's `Client Secret`.

The OpenTofu Keycloak client's secret can be retrieved via the Admin UI, navigate to the `UDS` Realm and select the `Clients` tab from the left sidebar, select the `uds-opentofu-client`, and click the `Credentials` tab to copy the secret value.

Here's an example configuration that would create a new client called `example-client`:
```hcl
terraform {
  required_providers {
    keycloak = {
      source  = "keycloak/keycloak"
      version = "5.5.0"
    }
  }
  required_version = ">= 1.0.0"
}

variable "keycloak_client_secret" {
  type        = string
  description = "Client secret for the Keycloak provider"
  sensitive   = true
}

provider "keycloak" {
  client_id     = "uds-opentofu-client"
  client_secret = var.keycloak_client_secret
  url           = "https://keycloak.admin.uds.dev"
  realm         = "uds"
}

# Create a new group in Keycloak
resource "keycloak_group" "example_group" {
  realm_id = "uds"
  name     = "example-group"

  # Optional attributes
  attributes = {
    description = "Example group created via Terraform"
    created_by  = "terraform"
  }
}

# Create a nested group under example-group
resource "keycloak_group" "nested_group" {
  realm_id  = "uds"
  name      = "nested-example-group"
  parent_id = keycloak_group.example_group.id  # This makes it a child of example-group

  attributes = {
    description = "Nested group under example-group"
    created_by  = "terraform"
  }

  lifecycle {
    prevent_destroy = false  # Set to true in production after testing
  }
}

# Output the group IDs for reference
output "example_group_id" {
  value       = keycloak_group.example_group.id
  description = "The ID of the example group"
}

output "nested_group_id" {
  value       = keycloak_group.nested_group.id
  description = "The ID of the nested group"
}
```

:::note
**Security Note:**
Passing sensitive values (such as `keycloak_client_secret`) via command line arguments can expose secrets in shell history and process lists. Instead, use a `.tfvars` file (e.g., `secrets.auto.tfvars`) to securely provide sensitive variables to Tofu.
:::

Create a file named `secrets.auto.tfvars` with the following content:

```hcl
keycloak_client_secret = "your-actual-client-secret-here"
```

Then run Tofu without passing the secret on the command line:

```bash
# Use this tofu command to plan the Tofu
tofu plan

# Use this tofu command to apply the Tofu
tofu apply -auto-approve
```

#### Enabling the OpenTofu Client via Keycloak Admin UI

If you need to enable the OpenTofu client after deployment or verify its configuration, follow these steps in the Keycloak Admin Console:

1. **Log in to Keycloak Admin Console**
   - Navigate to your Keycloak admin URL (typically `https://<your-keycloak-url>/admin/`)
   - Log in with administrative credentials
   - **Important**: Ensure you're in the `UDS` realm (not the `master` realm)
     - In the left sidecar, select `Manage Realms`
     - Select `uds` from the `Manage Realms` page

2. **Enable the Tofu Client**
   - In the left sidebar, click on "Clients"
   - Find the `uds-opentofu-client` client
   - Click on the client to open its settings
   - Toggle the "Enabled" switch to ON in the top right of the page
   - Click "Save" at the bottom of the page

#### Configure OpenTofu Client via Keycloak Admin UI

If you need to setup the OpenTofu client manually, the following steps will provide the steps to do this:

1. **Log in to Keycloak Admin Console**
   - Navigate to your Keycloak admin URL (typically `https://<your-keycloak-url>/admin/`)
   - Log in with administrative credentials
   - **Important**: Ensure you're in the `UDS` realm (not the `master` realm)
     - In the left sidecar, select `Manage Realms`
     - Select `uds` from the `Manage Realms` page

2. **Create new Client**
   - In the left sidebar, click on "Clients"
   - Click `Create client`
    - `Client ID` = `uds-opentofu-client`
    - `Name` = `uds-opentofu-client`
    - `Description` = `A client used for managing Keycloak via Tofu`
  - Click `Next`
    - Enable `Client authentication`
    - Disable `Standard flow`
    - Enable `Service account roles`
  - Click `Next`
  - Click `Save`
  - Click `Service account roles`
    - Click `Assign role`
      - Select `Client Roles`
        - Seach for `realm-admin` and check the box
        - `Assign`
  - Click `Credentials`
    - Copy the `Client Secret` and start applying Tofu
