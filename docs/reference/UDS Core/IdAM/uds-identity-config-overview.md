---
title: Overview
sidebar:
    order: 1
---

## What is IdAM?

Identity and Access Management (IdAM) refers to a framework of policies and technologies that ensure the proper people in an enterprise have the appropriate access to technology resources. IdAM systems provide tools and technologies for controlling user access to critical information within an organization through a set of business processes and by managing identities and access rights. The technology typically helps IT managers control user access to critical information within an organization by using a digital identity—which is considered unique in the system—and setting up roles, permissions, and policies. IdAM solutions ensure that users are who they claim to be (authentication) and that they can access the applications and resources they are allowed to use (authorization).

## What is UDS Identity Config?

UDS Identity Config is a component of the UDS Core that supplies the necessary configuration for Keycloak, an open-source Identity and Access Management solution. This configuration includes setting up realms, clients, roles, and other Keycloak settings tailored specifically for the UDS environment. By managing these configurations, UDS Identity Config facilitates the seamless integration of authentication and authorization functionalities into various services within the UDS ecosystem, ensuring that security protocols are consistently applied across platforms.

### Main Responsibilities
UDS Identity Config is responsible for managing several key aspects of Keycloak’s configuration within the UDS ecosystem, including:

1. Realm Configuration – Defines realms, clients, roles, and authentication flows.
2. Theme Configuration – Manages custom branding and UI elements for authentication pages.
3. Truststore Management – Ensures secure communication by handling trusted certificates and keys.
4. Custom Plugins – Supports additional functionality through custom Keycloak extensions and providers.

### Air-Gapped Limitations
When Keycloak is configured for X.509 certificate authentication and OCSP checking (x509-cert-auth.ocsp-checking-enabled) is enabled, it attempts to contact the OCSP responder specified in the certificate or a manually configured URL. In air-gapped or otherwise restricted environments, this external endpoint may be unreachable.

See this [bundle override](https://uds.defenseunicorns.com/reference/uds-core/idam/customization/#templated-realm-values) for an example of disabling OCSP checking but note the risks of doing so. By allowing certificates to pass when the revocation check fails, the door is open to revoked certificates being considered valid, which can pose a serious security threat depending on your organization’s compliance requirements and threat model.

### Upgrading UDS Identity Config
When upgrading UDS Identity Config, changes to the realm configuration do not propagate automatically. This is because Keycloak persists its realm settings across upgrades to prevent breaking existing functionality. To apply updates to the realm configuration, follow the manual steps outlined in [Upgrading Identity Config Versions](https://uds.defenseunicorns.com/reference/uds-core/idam/upgrading-versions/) .

However, updates to the following components are automatically applied upon upgrade, as they are not persisted between versions:

- Themes (branding and UI customizations)
- Truststore (certificate and key management)
- Custom Plugins (additional Keycloak extensions)

This ensures that realm configurations remain unchanged during upgrades, while other non-persistent settings are automatically refreshed.

## IdAM Contents

1. [Custom Image Testing and Deployment](https://uds.defenseunicorns.com/reference/uds-core/idam/testing-deployment-customizations/)
2. [Image Customizations](https://uds.defenseunicorns.com/reference/uds-core/idam/image-customizations/)
3. [Image Truststore Customization](https://uds.defenseunicorns.com/reference/uds-core/idam/truststore-customization/)
4. [Authentication Flows Customization](https://uds.defenseunicorns.com/reference/uds-core/idam/authentication-flows/)
5. [UDS Core Integration Testing](https://uds.defenseunicorns.com/reference/uds-core/idam/integration/)
6. [Custom Keycloak Plugins](https://uds.defenseunicorns.com/reference/uds-core/idam/plugin/)
7. [Upgrading Identity Config Versions](https://uds.defenseunicorns.com/reference/uds-core/idam/upgrading-versions/)
