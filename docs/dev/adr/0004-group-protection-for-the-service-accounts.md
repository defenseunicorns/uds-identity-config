# 4. Group Protection for the Service Accounts

Date: 2025-11-13

## Status

Discussion

## Glossary

- **Protocol Mapper**: A component in Keycloak that maps user attributes, roles, or other information (including hardcoded values) to token claims.
- **Service Account**: An account in Keycloak that represents an application or service rather than an individual user. Service Accounts are typically used for machine-to-machine authentication and authorization.
- **Client Credentials Grant**: An OAuth 2.0 flow used primarily for machine-to-machine authentication, where a client application can obtain an access token by presenting its own credentials (client ID and client secret) without user involvement.

## Context

The UDS Core exposes a standard way of configuring Keycloak Service Accounts via the Package CR (see the [UDS Core docs](https://uds.defenseunicorns.com/reference/configuration/single-sign-on/service-account/)). Tokens obtained with the Client Credentials grant associated with these Service Accounts do not include the `groups` claim. The primary reason for this behavior is that Service Accounts do not have a user identity as they represent an application tied with a Keycloak Client. Keycloak doesn't provide any tools around managing Service Account Groups as it uses Client Roles as a primary mechanism for controlling permissions associated with them. 

The lack of `groups` claim makes enforcing the [Group Protection](https://uds.defenseunicorns.com/reference/configuration/single-sign-on/group-based-auth/) impossible. Additionally, the Delivery Team has identified that there are certain applications (that are out of our control) that expect the `groups` claim to be present in tokens issued to Service Accounts.

Also related to this, UDS Identity Config provides specialized security hardening where users can specify only a subset of Protocol Mappers that are known to be safe and do not break security best practices enforced by the UDS Platform. A good example of an excluded Protocol Mapper is the `Hardcoded claim` mapper that allows adding arbitrary Claims to tokens, that can be used by a malicious actor to escalate privileges. The UDS Identity Config provides a way to manually add additional Protocol Mappers to the allow list (see the [Security Hardening section](https://uds.defenseunicorns.com/reference/uds-core/idam/plugin/#security-hardening)), which will be exposed for external configuration by "Expose a way to add additional Claims and Protocol Mappers to the allows list for UDSClientPolicyPermissionsExecutor [#697](https://github.com/defenseunicorns/uds-identity-config/issues/697)" ticket.

## Potential Solutions

1. Rely on adding the `groups` claim to the Service Accounts via the Package CR configuration and the `Hardcoded claim` Protocol Mapper

This option relies on pursuing the "Expose a way to add additional Claims and Protocol Mappers to the allows list for UDSClientPolicyPermissionsExecutor [#697](https://github.com/defenseunicorns/uds-identity-config/issues/697)" ticket. The next step is to add the `oidc-hardcoded-claim-mapper` (that supports hardcoding Claims into the token) to the allow list of Protocol Mappers. Once this is done, the user leverages the `spec.sso.[*].protocolMappers` and `spec.sso.[*].protocolMappers.Config` parts of the Package CR to add hardcoded `groups` claim to the Service Account tokens.

Advantages:

- No additional changes required

Disadvantages:

- Requires two-step configuration (adding Protocol Mapper to allow list and then configuring it on the Package CR)

2. Introduce the `/UDS Core/Admin/Service Account` and `/UDS Core/Auditor/Service Account` groups, and automatically add the appropriate `groups` claim to Service Account tokens

This approach relies on extending the existing Group/Permission model in UDS Core down to Service Accounts. With this approach, every Service Account that belongs to `/UDS Core/Auditor/Service Account` is automatically a member of the `/UDS Core/Auditor` group, which makes the Package CR configuration simpler. An additional Protocol Mapper would be automatically added to every Keycloak Client that has Service Account enabled, which would add the `groups` claim with proper values based on the Service Account group membership.

Advantages:

- Fine-level control over Service Account permissions

Disadvantages:

- Extends the existing Group/Permission model of the UDS Core Platform, which requires re-evaluating the Threat Model

3. Extend the existing Package CR `sso` spec with a protection mechanism based on custom claims

The implementation assumes extending the Package CR `sso` spec with a new mechanism custom claims checks, such as `azp` (that contains the name of the Service Account) or `preferred_username` (usually `service-account-<client-id>`). The UDS Core would adjust the `RequestAuthentication` to inspect the `@request.auth.claims.groups` (as described in the [Istio documentation](https://istio.io/latest/docs/tasks/security/authentication/jwt-route/#configuring-ingress-routing-based-on-jwt-claims)) and grant request access based on the presence of specific custom claims.

Advantages:

- Standardized approach for enforcing access based on custom claims
- Very flexible mechanism

Disadvantages:

- Slightly more complex configuration for end users

## Decision

TBD

## Consequences

TBD