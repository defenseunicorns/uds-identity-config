# Integration Testing For UDS Identity Config + UDS Core

Run `uds run uds-core-integration-tests` from the repository root. Setup generates
a fresh CAC client certificate and CA. It runs `uds run test-pki` on the generated
fixtures before building the image. Run that task separately to recheck them.
Cypress and HAProxy reuse the fixtures under `.cache/test-pki/`.

To rerun Cypress against the same deployment, run `uds run cypress-tests`.
The client certificate expires after 24 hours. Rerun setup to rotate the credentials
and deploy the new CA. Remove `.cache/test-pki/`
after tearing down the deployment. Parallel runs require separate checkouts and
Docker/cluster environments because the tasks share an image tag, cluster name,
and ports.
