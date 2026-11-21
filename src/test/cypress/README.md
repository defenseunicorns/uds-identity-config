# Integration Testing For UDS Identity Config + UDS Core

Run `uds run uds-core-integration-tests` from the repository root. CAC credentials
are generated automatically with OpenSSL and zip. One short-lived CA is shared by
the identity-config image, Istio gateways, HAProxy, and Cypress. Each run uses a
unique directory under the ignored `.cache/test-pki/` and removes it on exit.

For separate setup and test commands, keep the same environment variable:

```sh
export UDS_TEST_PKI_DIR="$PWD/.cache/test-pki/local-$(date +%s)"
uds run uds-core-integration-setup
uds run cypress-tests
rm -rf -- "$UDS_TEST_PKI_DIR"
```

Setup requires a new directory to avoid replacing an active deployment's CA.
Parallel integration runs need separate checkouts and Docker/cluster environments
because the existing tasks share an image tag, cluster name, and ports.

Run `uds run test-pki` for focused certificate and output checks. CI also runs the
full integration suite, which builds the image and tests CAC registration through
Istio and HAProxy. The production DoD archive retains its pinned checksum.
