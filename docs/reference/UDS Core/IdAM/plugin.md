---
title: Custom Keycloak Plugins
description: This documentation discusses the Keycloak plugin and the additional logic it provides.
---

A Keycloak plugin provides additional custom logic to our Keycloak deployment. Below is a table of the current implemented Custom Keycloak Implementations and how to interact with them.

## Current Custom Implementations

| Name | Type | Description |
|------|------|-------------|
| [Group Authentication](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/authentication/RequireGroupAuthenticator.java) | [Authenticator](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/authentication/Authenticator.html) | Define Keycloak group membership that is required to access an application. [Additional documentation](https://github.com/defenseunicorns/uds-core/blob/v0.23.0/docs/configuration/uds-operator.md?plain=1#L23-L26) and [E2E test](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/test/cypress/e2e/group-authz.cy.ts).|
| [Register Event Listener](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/eventListeners/RegisterEventListenerProvider.java) | [EventListener](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/events/EventListenerProvider.html) | Registration Event Listener to generate a unique id for each user that will be used as a `mattermostId`. [E2E test](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/test/cypress/e2e/registration.cy.ts#L49-L61). See Warnings below regarding this implementation. |
| [JSON Log Event Listener](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/eventListeners/JSONLogEventListenerProvider.java) | [EventListener](https://www.keycloak.org/docs-api/25.0.0/javadocs/org/keycloak/events/EventListenerProvider.html) | JSON Log Event listener converts Keycloak event logs into json strings for ease of use in Logging applications like Grafana. |
| [User Group Path Mapper](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/CustomGroupPathMapper.java) | [OpenID Mapper](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/protocol/oidc/mappers/AbstractOIDCProtocolMapper.html) | Some application break when using a forward slash in the group naming, this mapper removes the leading slash and creates a new `groups` claim called `bare-groups`. See Warnings below regarding the use of this plugin. |
| [User AWS SAML Group Mapper](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/CustomAWSSAMLGroupMapper.java) | [SAML Mapper](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/protocol/saml/mappers/AbstractSAMLProtocolMapper.html) | Amazon AppStream applications expect a specific group claim format when using Keycloak for authentication. This mapper filters the user’s groups to include only those with the substring `-aws-`. It then concatenates these qualified group paths into a colon-separated string that is passed in the SAML attribute. For example, if a user’s group hierarchy includes `/Core-aws/Admin-aws-test` and `/Core-aws/Auditor-aws-test`, the resulting SAML attribute value will be: `/Core-aws/Admin-aws-test:/Core-aws/Auditor-aws-test`. |

### Warnings

:::note
When creating a user via ADMIN API or ADMIN UI, the `REGISTER` event is not triggered, resulting in no Mattermost ID attribute generation. This will need to be done manually via click ops or the api. An example of how the attribute can be set via api can be seen [here](https://github.com/defenseunicorns/uds-common/blob/b2e8b25930c953ef893e7c787fe350f0d8679ee2/tasks/setup.yaml#L46).
:::

:::caution
Please use this scope only if you understand the implications of excluding full path information from group data. It is highly important to not use the `bare-groups` claim for protecting an application due to security vulnerabilities.
:::

## Requirements

Working on the plugin requires JDK17+ and Maven 3.5+.

```bash
# local java version
java -version

# loval maven version
mvn -version
```

## Plugin Testing with Keycloak

After making changes to the plugin code and verifying that unit tests are passing ( and hopefully writing some more ), test against Keycloak.

See the `New uds-identity-config Image` [section](https://uds.defenseunicorns.com/reference/uds-core/idam/testing-deployment-customizations/#build-a-new-image) for building, publishing, and using the new image with `uds-core`.

## Plugin Unit Testing / Code Coverage

The maven surefire and jacoco plugins are configured in the [pom.xml](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/plugin/pom.xml).

:::note
`mvn` commands will need to be executed from inside of the `src/plugin` directory
:::

:::note
There is a uds-cli task for running the `mvn clean verify` command: `uds run dev-plugin`.
:::

Some important commands that can be used when developing/testing on the plugin:

|Command|Description|
|-------|-----------|
| `mvn clean install` | Cleans up build artifacts and then builds and installs project into local maven repository. |
| `mvn clean test` | Cleans up build artifacts and then compiles the source code and runs all tests in the project. |
| `mvn clean test -Dtest=com.defenseunicorns.uds.keycloak.plugin.X509ToolsTest` | Same as `mvn clean test` but instead of running all tests in project, only runs the tests in designated file. |
| `mvn surefire-report:report` | This command will run the `mvn clean test` and then generate the surefire-report.html file in `target/site` |
| `mvn clean verify` | Clean project, run tests, and generate both surefire and jacoco reports |

### Viewing the Test Reports

```bash
# maven command from src/plugin directory
mvn clean verify
```

Open the `src/plugin/target/site/surefire-report.html` file in your browser to view the surefire test report.

Open the `src/plugin/target/site/jacoco/index.html` file in your browser to view the unit test coverage report generated by jacoco.

Both reports will hot reload each time they are regenerated, no need to open each time.

## New Custom Plugin Development

:::caution
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

### Configuration

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

### Disabling

If desired the Plugin can be removed from the identity-config image by commenting out these lines in the [Dockerfile](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/Dockerfile):

```bash
COPY plugin/pom.xml .
COPY plugin/src ../src

RUN mvn clean package
```
