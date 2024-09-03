---
title: Plugin
type: docs
weight: 2
---

A Keycloak plugin provides additional custom logic to our Keycloak deployment. Below is a table of the current implemented Custom Keycloak Implementations and how to interact with them.

## Current Custom Implementations

| Name | Type | Description |
|------|------|-------------|
| [Group Authentication](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/authentication/RequireGroupAuthenticator.java) | [Authenticator](https://www.keycloak.org/docs-api/25.0.0/javadocs/org/keycloak/authentication/Authenticator.html) | Define Keycloak group membership that is required to access an application. [Additional documentation](https://github.com/defenseunicorns/uds-core/blob/v0.23.0/docs/configuration/uds-operator.md?plain=1#L23-L26) and [E2E test](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/test/cypress/e2e/group-authz.cy.ts).|
| [Register Event Listener](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/eventListeners/RegisterEventListenerProvider.java) | [EventListener](https://www.keycloak.org/docs-api/25.0.0/javadocs/org/keycloak/events/EventListenerProvider.html) | Registration Event Listener to generate a unique id for each user that will be used as a `mattermostId`. [E2E test](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/test/cypress/e2e/registration.cy.ts#L49-L61). See Warnings below regarding this implementation. |
| [JSON Log Event Listener](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/eventListeners/JSONLogEventListenerProvider.java) | [EventListener](https://www.keycloak.org/docs-api/25.0.0/javadocs/org/keycloak/events/EventListenerProvider.html) | JSON Log Event listener converts Keycloak event logs into json strings for ease of use in Logging applications like Grafana. |
| [User Group Path Mapper](https://github.com/defenseunicorns/uds-identity-config/blob/v0.5.2/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/CustomGroupPathMapper.java) | [OpenID Mapper](https://www.keycloak.org/docs-api/25.0.0/javadocs/org/keycloak/protocol/oidc/mappers/AbstractOIDCProtocolMapper.html) | Some application break when using a forward slash in the group naming, this mapper removes the leading slash and creates a new `groups` claim called `bare-groups`. See Warnings below regarding the use of this plugin. |
| [User AWS SAML Group Mapper](https://github.com/defenseunicorns/uds-identity-config/blob/v0.6.0/src/plugin/src/main/java/com/defenseunicorns/uds/keycloak/plugin/CustomAWSSAMLGroupMapper.java) | [SAML Mapper](https://www.keycloak.org/docs-api/25.0.0/javadocs/org/keycloak/protocol/saml/mappers/AbstractSAMLProtocolMapper.html) | Amazon AppStream applications expect a specific group claim format when using Keycloak to pass authentication. This mapper allows for customizing the new attribute `name` field that will show up in SAML Requests that will contain the necessary concatenated groups string: `/parent-group/child-group1:/parent-group/child-group2`. |

### Warnings

{{% alert-note %}}
When creating a user via ADMIN API or ADMIN UI, the `REGISTER` event is not triggered, resulting in no Mattermost ID attribute generation. This will need to be done manually via click ops or the api. An example of how the attribute can be set via api can be seen [here](https://github.com/defenseunicorns/uds-common/blob/b2e8b25930c953ef893e7c787fe350f0d8679ee2/tasks/setup.yaml#L46).
{{% /alert-note %}}

{{% alert-caution %}}
Please use this scope only if you understand the implications of excluding full path information from group data. It is highly important to not use the `bare-groups` claim for protecting an application due to security vulnerabilities.
{{% /alert-caution %}}

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

See the `New uds-identity-config Image` section in the [CUSTOMIZE.md](./CUSTOMIZE.md#new-uds-identity-config-image) for building, publishing, and using the new image with `uds-core`.

## Plugin Unit Testing / Code Coverage

The maven surefire and jacoco plugins are configured in the [pom.xml](https://github.com/defenseunicorns/uds-identity-config/blob/main/src/plugin/pom.xml).

{{% alert-note %}}
`mvn` commands will need to be executed from inside of the `src/plugin` directory
{{% /alert-note %}}

{{% alert-note %}}
There is a uds-cli task for running the `mvn clean verify` command: `uds run dev-plugin`.
{{% /alert-note %}}

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
