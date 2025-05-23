---
title: Testing and Deployment Customizations
tableOfContents:
  maxHeadingLevel: 5
---

## Image Management

This document provides comprehensive guidelines for managing custom images within the UDS Core system, from creation and testing to deployment and transportation, particularly in restricted environments like air-gapped systems.

### Building and Testing Custom Images

#### Build a new image

Build a custom development image for UDS Core using the following commands:

```bash
# create a dev image uds-core-config:keycloak
uds run dev-build

# optionally, retag and publish to temporary registry for testing
docker tag uds-core-config:keycloak ttl.sh/uds-core-config:keycloak
docker push ttl.sh/uds-core-config:keycloak
```

#### Update UDS Core references

Update the custom image references in the `uds-core` repository:

* Update zarf.yaml to include updated image.
* Specify configImage in Keycloak values.yaml.
* For truststore updates, see gateway configuration instructions.

Alternatively, to override the existing Identity-Config image found in the Keycloak [values.yaml](https://github.com/defenseunicorns/uds-core/blob/main/src/keycloak/chart/values.yaml), use the following:
```yaml
overrides:
   keycloak:
      keycloak:
         values:
            - path: configImage
              value: ttl.sh/uds-core-config:keycloak
```

#### Deploy UDS Core

Deploy UDS Core with the new custom image:

```bash
# build and deploy uds-core
uds run test-uds-core
```

See [UDS Core](https://github.com/defenseunicorns/uds-core/blob/main/README.md) for further details

## Building New Image with Updates

For convenience, a Zarf package definition has been included to simplify custom image transport and install in air-gapped systems.

Use the included UDS task to build the custom image and package it with Zarf:

```bash
uds run build-zarf-pkg
```
