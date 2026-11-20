# Integration Testing For UDS Identity Config + UDS Core

Run the full suite from the repository root:

```sh
uds run uds-core-integration-tests
```

CAC credentials are generated automatically with OpenSSL and zip. Each run uses
one short-lived CA for the identity-config image, Istio gateways, HAProxy, and
Cypress. Credentials live under the ignored `.cache/test-pki/` directory and are
removed when the full test command exits, including on failure.

To keep a test deployment and its credentials for separate test invocations, set
an absolute path to a new directory before setup:

```sh
export UDS_TEST_PKI_DIR="$PWD/.cache/test-pki/local-$(date +%s)"
uds run uds-core-integration-setup
uds run cypress-tests
# Remove this directory when finished with the deployment.
rm -rf -- "$UDS_TEST_PKI_DIR"
```

Setup rejects an existing directory to avoid changing the CA underneath a running
deployment. Choose a new directory to repeat setup. Independent runs must use
separate checkouts and Docker/cluster environments because the integration tasks
share an image tag, cluster name, and ports.

Run the certificate and output checks with `uds run test-pki`. Set
`TEST_PKI_DOCKER_BUILD=true` to include a real truststore Docker build. CI runs
these checks, including loading the runtime config in Cypress, before the integration
suite. Local certificate checks also validate the Cypress config when its npm
dependencies and binary are installed. The production DoD CA archive retains
its separately pinned checksum.
