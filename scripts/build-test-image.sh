#!/usr/bin/env bash
# Copyright 2026 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -euo pipefail
: "${UDS_TEST_PKI_DIR:?Run integration setup with UDS_TEST_PKI_DIR set}"
test -f "$UDS_TEST_PKI_DIR/.complete"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
checksum="$(cat "$UDS_TEST_PKI_DIR/authorized_certs.zip.sha256")"
actual="$(shasum -a 256 "$UDS_TEST_PKI_DIR/authorized_certs.zip")"
if [[ "$checksum" != "${actual%% *}" ]]; then
  echo 'Generated CA archive checksum mismatch' >&2
  exit 1
fi
# Only the public CA archive enters the Docker context; each build owns its copy.
test_zip="$(mktemp src/truststore/.test-ca.XXXXXX)"
trap 'rm -f -- "$test_zip"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
cp "$UDS_TEST_PKI_DIR/authorized_certs.zip" "$test_zip"
docker build \
  --build-arg "CA_ZIP_URL=${test_zip##*/}" \
  --build-arg "CA_ZIP_SHA256=$checksum" \
  "$@" src
