# 5. Configuring and extending UDS Identity Config

Date: 2025-12-12

## Status

Discussion

## Context

### UDS Identity Config repo

Today, the UDS Identity Config repository is hosted separately from UDS Core. This model has its origins in [Platform One](https://repo1.dso.mil/big-bang/product/plugins/keycloak-p1-auth-plugin) and enables Mission Heroes to fully customize it (see the [docs](https://uds.defenseunicorns.com/reference/configuration/single-sign-on/keycloak-customization-guide/#approach-2-uds-identity-config-image)) without the need to fork UDS Core. At the same time, this separation introduces some challenges, such as test suite duality between UDS Core and UDS Identity Config, and development difficulties since part of the Keycloak plugin configuration resides in the UDS Core Keycloak Helm Chart and part in the UDS Identity Config.

### OpenTofu integration

UDS Identity Config provides [OpenTofu integration](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/#opentofu-keycloak-client-configuration) that enables Mission Heroes to customize the parts of the Keycloak Realm that do not have exposed Helm Chart configuration options.

This capability is intended for Mission Heroes, but adapting it directly in UDS Core will help address UDS Realm automatic migrations (which are done manually today; see the [Upgrading Versions](https://uds.defenseunicorns.com/reference/uds-core/idam/upgrading-versions/) section of the UDS documentation). In the future, the UDS CLI v2 will help provide better UX for using the Tofu integration.

### Mounting OCI Artifacts as volumes - [KEP-4639](https://github.com/kubernetes/enhancements/issues/4639)

Kubernetes 1.35 (targeted for December 17th, 2025) introduces a new feature that enables mounting OCI Artifacts (container images) as volumes in Pods. Version 1.35 is estimated to be the lowest supported version by October 2026. Once this happens, UDS Identity Config can be modified to remove the `sync.sh` script and mount files into proper directories instead.

### Keycloak Security Policies

The Keycloak upstream community is actively discussing a concept of Keycloak Security Policies that would inspect Keycloak configuration at boot time and log warning messages (or block the bootstrap procedure entirely) when certain criteria are not met (e.g. when an Authentication Flow doesn't contain certain execution steps).

The tool is primarily targeted for compliance (e.g., NIST SP 800-53, ASD STIG, etc.) but will expose all primitives to write custom policies and enforce them at boot time.

## Decision

The UDS Core and UDS Identity Config will maintain the existing status quo with the following assumptions:

* High-level opinionated configuration options will be provided via Helm Chart overrides
* OpenTofu will be used for minor configuration options that are not provided via Helm Chart overrides
* Mission Heroes will need to fork the UDS Identity Config image for non-standard use cases
* OpenTofu integration will be extended to UDS Core and used for automatic UDS Realm migrations

This decision will be revisited once Kubernetes 1.35 is widely adopted and Keycloak Security Policies are implemented.

## Consequences

* Continue the status quo
* Deprioritize any work for modularizing UDS Identity Config
* Focus on adapting OpenTofu integrations for automatic UDS Realm migrations in UDS Core

### Future improvements

Once KEP-4639 becomes mainstream and Keycloak Security Policies are implemented, UDS Identity Config can be redesigned into a more modular architecture:

- The UDS Keycloak Plugin
- The Realm JSON
- The UDS Theme

Such a modular architecture will enable Mission Heroes to pick and choose which parts of UDS Identity Config they want to use and which parts they want to customize on their own. At the same time, all parts of the Keycloak deployment could be provided as part of UDS Core, minimizing the maintenance burden for the UDS Foundations Team.