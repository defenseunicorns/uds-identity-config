# 5. Configuring and extending UDS Identity Config

Date: 2025-12-12

## Status

Discussion

## Context

### UDS Identity Config repo

Today the UDS Identity Config repository is hosted in a separate repository than UDS Core. This model has its beginning in [Platofrm One](https://repo1.dso.mil/big-bang/product/plugins/keycloak-p1-auth-plugin) and enables Mission Heroes to fully customize it (see the [docs](https://uds.defenseunicorns.com/reference/configuration/single-sign-on/keycloak-customization-guide/#approach-2-uds-identity-config-image)) without the need of forking UDS Core. At the same time, this separation introduces some challenges, such as testsuite duality between UDS Core and UDS Identity Config, and development difficulties as part of the Keycloak plugin configuration resides in UDS Core Keycloak Helm Chart and part in the UDS Identity Config.

### OpenTofu integration

UDS Identity Config provides [OpenTofu integration](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/#opentofu-keycloak-client-configuration) that enables Mission Heroes to customize these parts of the Keycloak Realm that do not have exposed Helm Chart configuration parts.

This capability aims to be used by the Mission Heroes but adapting it directly in UDS Core will help in addressing UDS Realm automatic migrations (which are done manually today, see the [Upgrading Versions](https://uds.defenseunicorns.com/reference/uds-core/idam/upgrading-versions/) part of the UDS documentation)). The UDS CLI v2 effort also plays a key role in providing better UX around modifying Keycloak Realm and combining the configuration with other parts of the UDS Core.

### Mounting OCI Artifacts as volumes - [KEP-4639](https://github.com/kubernetes/enhancements/issues/4639)

Kubernetes 1.35 (targeted on December 17th 2025) introduces a new feature that enables mounting OCI Artifacts (container images) as volumes in Pods. Version 1.35 is estimated to be the lowest supported version on October 2026. Once this happens, the UDS Identity Config can start taking advantage of it.

TBD: Mention getting rid of sync.sh

### [Keycloak Security Policies](TBD: link when the design is ready for review)

Keycloak upstream community heavily discusses a concept of Keycloak Security Policies that would inspect Keycloak configuration at boot time and log warn messages (or block the bootstrap procedure entirely) when certain criteria are not met - for example an Authentication Flow doesn't contain certain execution steps etc. 

The tool is primarily targeted for compliance (e.g. NIST SP 800-53, ASD STIG etc) but will expose all primitives to write custom compolicies and enforce them at the boot time.


### Future evolution proposal – splitting the UDS Identity Config up

Once KEP-4639 becomes mainstream and Keyclaok Security Policies are implemented, the UDS Identity Config can be redesigned into more modular architecture:

- The UDS Keycloak Plugin
- The Realm JSON
- The UDS Theme

Such a modular architecture will enable Mission Heroes to pick and choose which parts of the UDS Identity Config they want to use, and which parts they want to customize on their own. At the same time, all parts of the Keycloak deployment could be provided as part of the UDS Core and minimize the amount of maintenance burden for the UDS Foundations Team. 

## Decision

As outlined in the [Keycloak Clustomization Guide](https://uds.defenseunicorns.com/reference/configuration/single-sign-on/keycloak-customization-guide/), The UDS Identity Config will keep the existing Status Quo:

1. Leverage Helm Chart overrides for high-level opinionated configuration options
2. Use OpenTofu for all minor configuration options that are not provided (intentionally or unintenationally) via Helm Chart overrides
3. Fork the UDS Identity Config image for the non-standard use cases

This decision will be revisited once Kubernetes 1.35 is widely adopted and Keycloak Security Policies are implemented.

## Consequences

* Continuue the Status Quo
* Deprioritize any work for modularizing UDS IDentity Config.
* TBD: Focus on Tofu for migrations and internal consumption