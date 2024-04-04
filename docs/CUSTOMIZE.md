# Customizing uds-identity-config

These docs are intended for demonstrating how to customize the uds-core Identity (Keycloak) deployment by updating/changing the config image.  

* [Add additional jars (supported)](./CUSTOMIZE.md#add-additional-jars)
* [Customizing the theme  (supported)](./CUSTOMIZE.md#customize-theme)
* [Customizing the truststore (supported)](./CUSTOMIZE.md#customize-truststore)
* [Changing the realm values (supported)](./CUSTOMIZE.md#override-default-realm)
* [Disabling the UDS plugin (experimental)](./CUSTOMIZE.md#replace--disable-custom-plugin).

## Testing custom image in UDS Core

### Build a new image 
```bash
# create a dev image uds-core-config:keycloak
uds run dev-build

# optionally, retag and publish to temporary registry for testing
docker tag uds-core-config:keycloak ttl.sh/uds-core-config:keycloak
docker push ttl.sh/uds-core-config:keycloak
```

### Update UDS Core references
The custom image reference will need to be update in a few places in the `uds-core` repository:
* Update [zarf.yaml](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/zarf.yaml#L24) to include updated image 
* Specify `configImage` in Keycloak [values.yaml](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10)
* If the truststore has been updated, see [gateway configuration instructions](./CUSTOMIZE.md#configure-istio-gateways-cacert-in-uds-core)


### Deploy UDS Core
```bash
# build and deploy uds-core 
uds run test-uds-core
```

See [UDS Core](https://github.com/defenseunicorns/uds-core/blob/main/README.md) for further details

# Customizations
## Add additional jars

Adding additional jars to Keycloak's deployment is as simple as adding that jar to the [src/extra-jars directory](../src/extra-jars/).

Adding new jars will require building a new identity-config image for [uds-core](https://github.com/defenseunicorns/uds-core).

See [Testing custom image in UDS Core](./CUSTOMIZE.md#testing-custom-image-in-uds-core) for building, publishing, and using the new image with `uds-core`.

Once `uds-core` has sucessfully deployed with your new image, viewing the Keycloak pod can provide insight into a successful deployment or not. Also describing the Keycloak pod, should display your new image being pulled instead of the default image defined [here](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10) in the events section.

## Customize Theme

#### Official Theming Docs

- [Official Keycloak Theme Docs](https://www.keycloak.org/docs/latest/server_development/#_themes)
- [Official Keycloak Theme Github](https://github.com/keycloak/keycloak/tree/b066c59a83c99d757d501d8f5e6061372706d24d/themes/src/main/resources/theme)

Changes can be made to the [src/theme](../src/theme) directory. At this time only Account and Login themes are included, but could be changed to include email, admin, and welcome themes as well.

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

The `UDS Identity` realm is defined in the realm.json found in [src/realm.json](../src/realm.json). This can be modified and will require a new `uds-identity-config` image for `uds-core`. 

> [!CAUTION]
> Be aware that changing values in the realm may also need be to updated throughout the configuration of Keycloak and Authservice in `uds-core`. For example, changing the realm name will break a few different things within Keycloak unless those values are changed in `uds-core` as well.

See the [Testing custom image in UDS Core](./CUSTOMIZE.md#testing-custom-image-in-uds-core) for building, publishing, and using the new image with `uds-core`.

## Customize Truststore
The default truststore is configured in a [script](../src/truststore/ca-to-jks.sh) and excuted in the [Dockerfile](../src/Dockerfile). There is a few different ways the script could be customized. 

- [Change where the DoD CA zip file are pulled from.](../src/Dockerfile#L31), defualting to DOD UNCLASS certs but could be updated for local or another source.
- [Change the Regex Exclusion Filter](../src/Dockerfile#30), used by the ca-to-jks script to exclude certain certs from being added to the final truststore.
- [Change the truststore password](../src/truststore/ca-to-jks.sh#L29)

#### Build test `authorized_certs.zip`
Utilizing the [`regenerate-test-pki` task](../tasks.yaml), you can create a test `authorized_certs.zip` to use for the truststore. 

To use the `regenerate-test-pki` task:


* Create `csr.conf`
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

* ```bash
   # Generates new authorized_certs.zip
   uds run regenerate-test-pki
   ```

#### Update Dockerfile and build image
Update `CA_ZIP_URL` in [Dockerfile](.../src/Dockerfile) to refer to the generated `authorized_certs.zip`

```
ARG CA_ZIP_URL=authorized_certs.zip
```

Build config image
```bash
# build image
uds run dev-build
```

>[!TIP]
> If you're getting errors from the ca-to-jks.sh script, verify your zip folder is in the correct directory.

#### Configure Istio Gateways CACERT in UDS Core
```bash
# In `uds-core` create cacert from the new identity-config image
uds run -f src/keycloak/tasks.yaml cacert --set IMAGE_NAME=<identity config image> --set VERSION=<identity config image version>
```

```bash
# Update tenant and admin gateway with generated cacerts
uds run -f src/keycloak/tasks.yaml dev-cacert
```

#### Deploy UDS Core with new uds-identity-config
See [Testing custom image in UDS Core](../CUSTOMIZE.md#testing-custom-image-in-uds-core)

#### Verify Istio Gateway configuration

```bash
# Verify the "Acceptable client certificate CA names"
openssl s_client -connect sso.uds.dev:443
```

## Custom Plugin
> [!IMPORTANT]
> This isn't recommended, however can be achieved if necessary

> [!TIP]
> Making these changes iteratively and importing into Keycloak to create a new realm can help to alleviate typo's and mis-configurations. This is also the quickest solution for testing without having to create,build,deploy with new images each time.

The plugin provides the auth flows that keycloak uses for x509 (CAC) authentication as well as some of the surrounding registration flows.

#### Developing

See [PLUGIN.md](./PLUGIN.md).

#### Configuration

In addition, modify the realm for keycloak, otherwise the realm will require plugin capabilities for registering and authenticating users. In the current [realm.json](../src/realm.json) there is a few sections specifically using the plugin capabilities. Here is the following changes necessary:
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

#### Disabling

If desired the Plugin can be removed from the identity-config image by commenting out these lines in the [Dockerfile](../src/Dockerfile):

```
COPY plugin/pom.xml .
COPY plugin/src ../src

RUN mvn clean package
```

#### Building New Image with Updates

Once satisfied with changes and tested that they work, see [Testing custom image in UDS Core](./CUSTOMIZE.md#testing-custom-image-in-uds-core) for building, publishing, and using the new image with `uds-core`.


## Transport Custom Image with Zarf
For convenience, a Zarf package definition has been included to simplify custom image transport and install in air-gapped systems.

#### Build the Zarf package
Use the included UDS task to build the custom image and package it with Zarf:
```
uds run build-zarf-pkg
```