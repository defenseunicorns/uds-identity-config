# UDS Identity Config

This repo builds the UDS Identity (keycloak) Config image used by UDS Identity. Utilize this repository to create your own Keycloak config image for customizing `uds-core`'s [Identity deployment](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml#L10).

---

 In this repo you'll find the following:

- `src/extra-jars` - Place any extra jars here to be added to the Keycloak server
- `src/Dockerfile` - The Dockerfile used to build the image, `CA_ZIP_URL` and `CA_REGEX_EXCLUSION_FILTER` builds args control the Trust Store
- `src/plugin` - A custom Keycloak plugin to add x509 certificate auto-enrollment
- `src/theme` - A custom Keycloak theme for the login, registration, and account management pages
- `src/truststore` - A script to pull CA certs into a truststore, using the docker build ARGs for `CA_ZIP_URL` (remote URL to a zip file with CA certs) and `CA_REGEX_EXCLUSION_FILTER` (regex for certs to exclude)
- `src/realm.json` - The UDS Identity realm configuration used by Keycloak on startup (if the realm doesn't exist)

---

## Tasks

Available tasks used by `uds run <task name>` also can be view via command line by running `uds run --list`.

| Task Name | Task Description |
|---------------------|---------------------------------------------|
| build-and-publish   | Build and publish the multi-arch image      |
| dev-build           | Build the image locally for dev             |
| dev-update-image    | Build the image and import locally into k3d |
| dev-theme           | Copy theme to Keycloak in dev cluster       |
| cacert              | Get the CA cert value for the Istio Gateway |
| debug-istio-traffic | Debug Istio traffic on keycloak             |
| regenerate-test-pki | Generate a PKI cert for testing             |

---

## Add additional jars



---

## Customize Theme
#### Official Docs

- [Official Keycloak Theme Docs](https://www.keycloak.org/docs/latest/server_development/#_themes)
- [Official Keycloak Theme Github](https://github.com/keycloak/keycloak/tree/b066c59a83c99d757d501d8f5e6061372706d24d/themes/src/main/resources/theme)

#### Testing Changes
To test the `identity-config` theme changes, a local running Keycloak instance is required.

Don't have a local Keycloak instance? The simplest testing path is utilizing [uds-core](https://github.com/defenseunicorns/uds-core), specifically the `identity-setup` task. This will create a k3d cluster with Istio, Pepr, Keycloak, and Authservice.

Once that cluster is up and healthy, there is a fancy task in this repo that can be utilized to hot-reload theme changes for testing purposes.

1. Setup [uds-core](https://github.com/defenseunicorns/uds-core) cluster by utilizing this command in the `uds-core` repo: `uds run identity-setup`
2. With the theme changes in this repo, executing this command: `uds run dev-theme`
3. View the changes in the browser

---

## Customize Truststore



---

## Override Default Realm

The default `UDS Identity` realm is defined in the realm.json found in [src/realm.json](./src/realm.json). 

---

## Replace / Disable Custom Plugin



---
