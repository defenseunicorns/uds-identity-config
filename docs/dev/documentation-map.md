# UDS Identity Config Documentation Map

> **Migration Reference Only** — This document was created to support the migration of user-facing documentation from `uds-identity-config` to `uds-core`. It is not intended to be maintained long-term. Once the migration is complete and the `docs/reference/uds-core/idam/` directory is removed, this file can be deleted.

## Purpose

This document maps the former identity-config documentation topics (`docs/reference/uds-core/idam/`) to their new locations on the [UDS Core documentation site](https://docs.defenseunicorns.com/core/).

All user-facing documentation now lives in the [uds-core](https://github.com/defenseunicorns/uds-core) repository under `docs/`.

---

## Concepts

High-level explanations of what identity and access management is, how Keycloak fits into UDS Core, and the three configuration layers (Helm values, UDS Identity Config image, OpenTofu/IaC).

| Topic | URL |
| --- | --- |
| Identity & Authorization overview | [docs.defenseunicorns.com/core/concepts/core-features/identity-and-authorization/](https://docs.defenseunicorns.com/core/concepts/core-features/identity-and-authorization/) |

---

## How-To Guides

Task-oriented guides for platform engineers configuring identity and access management.

### Authentication & Authorization

| Topic | URL |
| --- | --- |
| Configure authentication flows (username/password, X509, SSO, MFA) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-authentication-flows/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-authentication-flows/) |
| Enforce group-based access control | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/enforce-group-based-access/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/enforce-group-based-access/) |
| Protect apps with Authservice (non-OIDC SSO) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/protect-apps-with-authservice/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/protect-apps-with-authservice/) |
| Register and customize SSO clients | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/register-and-customize-sso-clients/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/register-and-customize-sso-clients/) |
| Configure service accounts (client credentials) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-service-accounts/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-service-accounts/) |
| Configure device flow (CLI/headless) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-device-flow/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-device-flow/) |

### Identity Providers

| Topic | URL |
| --- | --- |
| Connect Azure AD (Entra ID) as IdP | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/connect-azure-ad-idp/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/connect-azure-ad-idp/) |
| Configure Google as IdP | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-google-idp/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-google-idp/) |

### Session & Account Security

| Topic | URL |
| --- | --- |
| Configure login policies (session timeouts, concurrent sessions) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-keycloak-login-policies/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-keycloak-login-policies/) |
| Configure account lockout (brute-force protection) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-account-lockout/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-account-lockout/) |
| Configure user account settings (email, password policy, security hardening) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-user-account-settings/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-user-account-settings/) |

### Certificates & Truststore

| Topic | URL |
| --- | --- |
| Configure custom truststore (CA certificates for X.509/CAC) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-truststore/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-truststore/) |
| Configure X.509 CRL for air-gapped environments | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-x509-crl-airgap/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-x509-crl-airgap/) |

### Customization & Image Building

| Topic | URL |
| --- | --- |
| Customize branding (logo, T&C, registration fields) | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/customize-branding/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/customize-branding/) |
| Build and deploy a custom identity-config image | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/build-deploy-custom-image/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/build-deploy-custom-image/) |
| Enable FIPS mode | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/enable-fips-mode/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/enable-fips-mode/) |

### Infrastructure as Code

| Topic | URL |
| --- | --- |
| Manage Keycloak with OpenTofu | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/manage-keycloak-with-opentofu/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/manage-keycloak-with-opentofu/) |

### Resilience

| Topic | URL |
| --- | --- |
| Configure Keycloak HTTP retries | [docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-keycloak-http-retries/](https://docs.defenseunicorns.com/core/how-to-guides/identity-and-authorization/configure-keycloak-http-retries/) |

### High Availability

| Topic | URL |
| --- | --- |
| Keycloak HA (external DB, HPA, anti-affinity) | [docs.defenseunicorns.com/core/how-to-guides/high-availability/keycloak/](https://docs.defenseunicorns.com/core/how-to-guides/high-availability/keycloak/) |
| Authservice HA (Redis session store, scaling) | [docs.defenseunicorns.com/core/how-to-guides/high-availability/authservice/](https://docs.defenseunicorns.com/core/how-to-guides/high-availability/authservice/) |

---

## Reference

Configuration surfaces, environment variables, defaults, and plugin documentation.

| Topic | URL |
| --- | --- |
| Identity & Authorization configuration reference (realm variables, auth flows, themes, plugins, lockout, truststore, FIPS) | [docs.defenseunicorns.com/core/reference/configuration/identity-and-authorization/](https://docs.defenseunicorns.com/core/reference/configuration/identity-and-authorization/) |

---

## Operations

Day-2 operational procedures, upgrades, and troubleshooting.

| Topic | URL |
| --- | --- |
| Upgrade Keycloak realm configuration (manual realm changes) | [docs.defenseunicorns.com/core/operations/upgrades/upgrade-keycloak-realm/](https://docs.defenseunicorns.com/core/operations/upgrades/upgrade-keycloak-realm/) |
| Keycloak credential recovery | [docs.defenseunicorns.com/core/operations/troubleshooting-and-runbooks/keycloak-credential-recovery/](https://docs.defenseunicorns.com/core/operations/troubleshooting-and-runbooks/keycloak-credential-recovery/) |
| Release notes (version-specific identity-config migration steps) | [docs.defenseunicorns.com/core/operations/release-notes/overview/](https://docs.defenseunicorns.com/core/operations/release-notes/overview/) |
