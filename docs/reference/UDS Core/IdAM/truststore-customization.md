---
title: Truststore Customization
tableOfContents:
  maxHeadingLevel: 5
---

## Customizing Truststore

The default truststore is configured in a [script](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/truststore/ca-to-jks.sh) and excuted in the [Dockerfile](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/Dockerfile). There is a few different ways the script could be customized.

* [Change where the DoD CA zip file are pulled from.](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/Dockerfile#L31), defualting to DOD UNCLASS certs but could be updated for local or another source.
* [Change the Regex Exclusion Filter](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/Dockerfile#30), used by the ca-to-jks script to exclude certain certs from being added to the final truststore.
* [Change the truststore password](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/truststore/ca-to-jks.sh#L29)

### Build test `authorized_certs.zip`

Utilizing the [`regenerate-test-pki` task](https://github.com/defenseunicorns/uds-identity-config/blob/main/tasks.yaml), you can create a test `authorized_certs.zip` to use for the truststore.

To use the `regenerate-test-pki` task:

* Create `csr.conf`

   ```bash
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

### Update Dockerfile and build image

Update `CA_ZIP_URL` in [Dockerfile](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/Dockerfile) to refer to the generated `authorized_certs.zip`

```bash
ARG CA_ZIP_URL=authorized_certs.zip
```

Build config image

```bash
# build image
uds run dev-build
```

:::note
If you're getting errors from the ca-to-jks.sh script, verify your zip folder is in the correct directory.
:::

### Configure Istio Gateways CACERT in UDS Core

```bash
# In `uds-core` create cacert from the new identity-config image
uds run -f src/keycloak/tasks.yaml cacert --set IMAGE_NAME=<identity config image> --set VERSION=<identity config image version>
```

```bash
# Update tenant and admin gateway with generated cacerts
uds run -f src/keycloak/tasks.yaml dev-cacert
```

### Deploy UDS Core with new uds-identity-config

See [Testing custom image in UDS Core](https://uds.defenseunicorns.com/reference/uds-core/idam/testing-deployment-customizations/)

### Verify Istio Gateway configuration

```bash
# Verify the "Acceptable client certificate CA names"
openssl s_client -connect sso.uds.dev:443
```