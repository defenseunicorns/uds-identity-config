# Plugin Development

The Keycloak plugin is a custom plugin that is used for x509 certificate enrollment.

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

The maven surefire plugin is configured in the [pom.xml](./src/plugin/pom.xml). Some important commands that can be used when developing/testing on the plugin:

> [!IMPORTANT]
> `mvn` commands will need to be executed from inside of the `src/plugin` directory

|Command|Description|
|-------|-----------|
| `mvn clean install` | Cleans up build artifacts and then builds and installs project into local maven repository. |
| `mvn clean test` | Cleans up build artifacts and then compiles the source code and runs all tests in the project. |
| `mvn clean test -Dtest=com.defenseunicorns.uds.keycloak.plugin.X509ToolsTest` | Same as `mvn clean test` but instead of running all tests in project, only runs the tests in designated file. |
| `mvn surefire-report:report` | This command will run the `mvn clean test` and then generate the surefire-report.html file in `target/site` |

### Viewing the Maven Surefire Reports

```bash
# maven command from src/plugin directory
mvn surefire-report:report

# uds command from base directory
uds run dev-plugin
```

Open the `src/plugin/target/site/index.html` file in your browser to view the test coverage. This will hot reload each time the site folder is rebuilt.

Sometimes IDE's won't allow opening files in a browser, either download an extension for managing this or open it from file explorer.
