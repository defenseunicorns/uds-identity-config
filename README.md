# UDS Identity Config

This repo builds the UDS Identity (keycloak) Config image used by UDS Identity. In this repo you'll find the following:

- `src/extra-jars` - Place any extra jars here to be added to the Keycloak server
- `src/Dockerfile` - The Dockerfile used to build the image, `CA_ZIP_URL` and `CA_REGEX_EXCLUSION_FILTER` builds args control the Trust Store
- `src/plugin` - A custom Keycloak plugin to add x509 certificate auto-enrollment
- `src/theme` - A custom Keycloak theme for the login, registration, and account management pages
- `src/truststore` - A script to pull CA certs into a truststore, using the docker build ARGs for `CA_ZIP_URL` (remote URL to a zip file with CA certs) and `CA_REGEX_EXCLUSION_FILTER` (regex for certs to exclude)
- `src/realm.json` - The UDS Identity realm configuration used by Keycloak on startup (if the realm doesn't exist)

Also included are a number of UDS tasks under `tasks.yaml`. Each task can be viewed with its name and description using `uds run --list`.
