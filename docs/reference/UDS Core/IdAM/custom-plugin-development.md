---
title: Custom Plugin Development
tableOfContents:
  maxHeadingLevel: 5
---

# Custom Plugin

:::note
This isn't recommended, however can be achieved if necessary
:::

:::note
Making these changes iteratively and importing into Keycloak to create a new realm can help to alleviate typo's and mis-configurations. This is also the quickest solution for testing without having to create,build,deploy with new images each time.
:::

The plugin provides the auth flows that keycloak uses for x509 (CAC) authentication as well as some of the surrounding registration flows.

One nuanced auth flow is the creation of a Mattermost ID attribute for users. [CustomEventListener](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/eventListeners/RegisterEventListenerProvider.java) is responsible for generating the unique ID.

:::note
When creating a user via ADMIN API or ADMIN UI, the 'REGISTER' event is not triggered, resulting in no Mattermost ID attribute generation. This will need to be done manually via click ops or the api. An example of how the attribute can be set via api can be seen [here](https://github.com/defenseunicorns/uds-common/blob/b2e8b25930c953ef893e7c787fe350f0d8679ee2/tasks/setup.yaml#L46).
:::

# Developing

See [PLUGIN.md](./PLUGIN.md).

# Configuration

In addition, modify the realm for keycloak, otherwise the realm will require plugin capabilities for registering and authenticating users. In the current [realm.json](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/realm.json) there is a few sections specifically using the plugin capabilities. Here is the following changes necessary:

* Remove all of the `UDS ...` authenticationFlows:
  * `UDS Authentication`
  * `UDS Authentication Browser - Conditional OTP`
  * `UDS Registration`
  * `UDS Reset Credentials`
  * `UDS registration form`

* Make changes to authenticationExecutions from the `browser` authenticationFlow:
  * Remove `auth-cookie`
  * Remove `auth-spnego`
  * Remove `identity-provider-redirector`
  * Update the remaining authenticationFlow
    * `"requirement": "REQUIRED"`
    * `"flowAlias": "Authentication"`

* Remove `registration-profile-action` authenticationExecution from the `registration form` authenticationFlow

* Update the realm flows:
  * `"browserFlow": "browser"`
  * `"registrationFlow": "registration"`
  * `"resetCredentialsFlow": "reset credentials"`

# Disabling

If desired the Plugin can be removed from the identity-config image by commenting out these lines in the [Dockerfile](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/Dockerfile):

```bash
COPY plugin/pom.xml .
COPY plugin/src ../src

RUN mvn clean package
```