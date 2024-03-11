# Customizing uds-identity-config

These docs are intended for demonstrating how to customize the uds-core Identity (Keycloak) deployment by updating/changing the config values.

## New `uds-identity-config` Image

   Make changes, [additional jars](README.md#add-additional-jars), [customizing the theme](README.md#customize-theme), [changing the realm values](README.md#override-default-realm), [customizing the truststore](README.md#customize-truststore), or [disabling the custom plugin](README.md#replace--disable-custom-plugin).

   1. Once changes have been made, create the image:
      - Use the [dev-build](./tasks.yaml#L17) task to build a local image, which by default creates `uds-core-config:keycloak` image. Either update that task for your image name or re-tag that image after. 
         ```bash
            docker tag uds-core-config:keycloak ttl.sh/uds-core-config:keycloak
         ```

   2. If accessing Keycloak UI's (admin portal, user info portal, etc.) is required, it's simplest to push the image to a public registry (this is because ISTIO and Zarf init will be necessary but the zarf image registry lifecycle can be complicated if not pulling an image from a registry). We have used [ttl.sh](https://github.com/replicatedhq/ttl.sh) for our testing.
      ```bash
         docker push ttl.sh/uds-core-config:keycloak
      ```

   3. The newly created image will need to be referenced in a few places in the `uds-core` repo;
      - [zarf.yaml](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/zarf.yaml#L24)
      - Either Flavor values yaml
        - [upstream flavor values](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/values/upstream-values.yaml) (default flavor)
        - [registry1 flavor values](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/values/registry1-values.yaml)
         
         The default values.yaml are specified [here](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10), but will be overridden by the flavor values.

   4. Deploy uds-core: 
      - If being able to access the different Keycloak UI's is required, utilize the `uds-core` task `dev-identity`, this will install ISTIO, PEPR, Keycloak, and Authservice.
      - Otherwise, if Keycloak UI access isn't required, the quickest solution is to use the `uds-core` task `test-single-package`. This task can utilize local images and doesn't require the images be pushed up to a public registry.
         ```bash
         UDS_PKG=keycloak uds run test-single-package
         ```

   5. [Accessing Keycloak and other documentation interacting with Keycloak](https://github.com/defenseunicorns/uds-core/blob/main/README.md#testing-uds-core-keycloak-and-authservice)

## Add additional jars

   Adding additional jars to Keycloak's deployment is as simple as adding that jar to the [src/extra-jars directory](./src/extra-jars/).

   Adding new jars will require building a new identity-config image for [uds-core](https://github.com/defenseunicorns/uds-core).

   See the [New uds-identity-config Image section](./README.md#new-uds-identity-config-image) for building, publishing, and using the new image with `uds-core`.

   Once `uds-core` has sucessfully deployed with your new image, viewing the Keycloak pod can provide insight into a successful deployment or not. Also describing the Keycloak pod, should display your new image being pulled instead of the default image defined [here](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10) in the events section.

## Customize Theme

   #### Official Theming Docs

   - [Official Keycloak Theme Docs](https://www.keycloak.org/docs/latest/server_development/#_themes)
   - [Official Keycloak Theme Github](https://github.com/keycloak/keycloak/tree/b066c59a83c99d757d501d8f5e6061372706d24d/themes/src/main/resources/theme)

   Changes can be made to the [src/theme](./src/theme) directory. At this time only Account and Login themes are included, but could be changed to include email, admin, and welcome themes as well.

   #### Testing Changes
   To test the `identity-config` theme changes, a local running Keycloak instance is required.

   Don't have a local Keycloak instance? The simplest testing path is utilizing [uds-core](https://github.com/defenseunicorns/uds-core), specifically the `dev-identity` task. This will create a k3d cluster with Istio, Pepr, Keycloak, and Authservice.

   Once that cluster is up and healthy and after making theme changes:

   1. Execute this command: 
      ```bash
         uds run dev-theme
      ```
   2. View the changes in the browser

## Override Default Realm

   The `UDS Identity` realm is defined in the realm.json found in [src/realm.json](./src/realm.json). This can be modified and will require a new `uds-identity-config` image for `uds-core`. 

   > [!CAUTION]
   > Be aware that changing values in the realm may also need be to updated throughout the configuration of Keycloak and Authservice in `uds-core`. For example, changing the realm name will break a few different things within Keycloak unless those values are changed in `uds-core` as well.

   See the [New uds-identity-config Image section](./README.md#new-uds-identity-config-image) for building, publishing, and using the new image with `uds-core`.

## Customize Truststore
   The default truststore is configured in a [script](./src/truststore/ca-to-jks.sh) and excuted in the [Dockerfile](./src/Dockerfile). There is a few different ways the script could be customized. 

   - [Change where the DoD CA zip file is pulled from.](./src/Dockerfile#L31), defualting to DOD certs but could be updated for local or another source.
   - [Change the Regex Exclusion Filter](./src/Dockerfile#30), used by the ca-to-jks script to exclude certain certs from being added to the final truststore.
   - [Change the truststore password](./src/truststore/ca-to-jks.sh#L29)

   #### BYOP - Bring your own PKI
   Utilizing the [`regenerate-test-pki` task](./tasks.yaml), you can create a test PKI to use for the truststore. This is also how you can bring your own PKI. 

   To use the `regenerate-test-pki` task, you will need a csr.conf file wherever you're running the task from. An example conf file could look like this:

   ```
   [req]
   default_bits       = 2048
   default_keyfile    = key.pem
   distinguished_name = req_distinguished_name
   req_extensions     = req_ext
   x509_extensions    = v3_ext

   [req_distinguished_name]
   countryName                 = Country Name (2 letter code)
   countryName_default         = US
   stateOrProvinceName         = State or Province Name (full name)
   stateOrProvinceName_default = Colorado
   localityName                = Locality Name (eg, city)
   localityName_default        = Colorado Springs
   organizationName            = Organization Name (eg, company)
   organizationName_default    = Defense Unicorns
   commonName                  = Common Name (e.g. server FQDN or YOUR name)
   commonName_default          = uds.dev

   [req_ext]
   subjectAltName = @alt_names

   [v3_ext]
   subjectAltName = @alt_names

   [alt_names]
   DNS.0 = *.uds.dev
   ```

   Once the new test.cer file has been created we bundle it up in a zip folder and update the [Dockerfile](./src/Dockerfile) to point to the local zip folder instead of pulling that zip from the DOD PKI source.

   ```
   ARG CA_ZIP_URL=authorized_certs.zip
   ```

   >[!TIP]
   > If you're getting errors from the ca-to-jks.sh script, verify your zip folder is in the correct directory.

   Now you can follow steps 1-3 in [New uds-identity-config Image section](./README.md#new-uds-identity-config-image) for building, publishing, and using the identity-config image with `uds-core`. However we need to wait to deploy `uds-core` till we update the ISTIO cacerts with the newly published identity-config image.
   
   In `uds-core` there are additional steps for getting this new truststore to work because ISTIO will need to be updated:
      1. In `uds-core` create cacert from the new identity-config image
         ```bash
            uds run -f src/keycloak/tasks.yaml cacert --set IMAGE_NAME=<identity config image> --set VERSION=<identity config image version>
         ```
      2. Copy the created cacert.b64 contents and overwrite the cacert field in these two files:
            - [config-tenant.yaml](https://github.com/defenseunicorns/uds-core/blob/main/src/istio/values/config-tenant.yaml#L12)
            - [config-admin.yaml](https://github.com/defenseunicorns/uds-core/blob/main/src/istio/values/config-admin.yaml#L13)
      3. Now you can go back and complete the 4th step in the [New uds-identity-config Image section](./README.md#new-uds-identity-config-image) for deploying `uds-core` with the identity-config image.

   #### Verify New Cert

   ```bash
   openssl s_client -connect sso.uds.dev:443
   ```
   Using this command will output client ssl cert information which you can use to verify the use of the new cert.


## Replace / Disable Custom Plugin
> [!IMPORTANT]
> This isn't recommended, however can be achieved if necessary

   The plugin provides the auth flows that keycloak uses for x509 (CAC) authentication as well as some of the surrounding registration flows.

   If desired the Plugin can be removed from the identity-config image by commenting out these lines in the [Dockerfile](./src/Dockerfile):

   ```
   COPY plugin/pom.xml .
   COPY plugin/src ./src

   RUN mvn clean package
   ```

   In addition, modify the realm for keycloak, otherwise the realm will require plugin capabilities for registering and authenticating users. In the current [realm.json](./src/realm.json) there is a few sections specifically using the plugin capabilities. Here is the following changes necessary:
   - Remove all of the `UDS ...` authenticationFlows:
      - `UDS Authentication`
      - `UDS Authentication Browser - Conditional OTP`
      - `UDS Registration`
      - `UDS Reset Credentials`
      - `UDS registration form`

   - Make changes to authenticationExecutions from the `browser` authenticationFlow:
      - Remove `auth-cookie`
      - Remove `auth-spnego`
      - Remove `identity-provider-redirector`
      - Update the remaining authenticationFlow
         - `"requirement": "REQUIRED"`
         - `"flowAlias": "Authentication"`

   - Remove `registration-profile-action` authenticationExecution from the `registration form` authenticationFlow

   - Update the realm flows:
      - `"browserFlow": "browser"`
      - `"registrationFlow": "registration"`
      - `"resetCredentialsFlow": "reset credentials"`

   > [!TIP]
   > Making these changes iteratively and importing into Keycloak to create a new realm can help to alleviate typo's and mis-configurations. This is also the quickest solution for testing without having to create,build,deploy with new images each time.

   Once satisfied with changes and tested that they work, see the [New uds-identity-config Image section](./README.md#new-uds-identity-config-image) for building, publishing, and using the new image with `uds-core`.
