#!/usr/bin/env bash
# Copyright 2026 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -euo pipefail
umask 077

: "${UDS_TEST_PKI_DIR:?Set UDS_TEST_PKI_DIR to a new absolute directory for this test run}"
case "$UDS_TEST_PKI_DIR" in
  /*) ;;
  *) echo 'UDS_TEST_PKI_DIR must be absolute' >&2; exit 1 ;;
esac
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Reserve the directory exclusively. Never replace credentials used by another step.
mkdir -p "$(dirname "$UDS_TEST_PKI_DIR")"
mkdir -m 700 "$UDS_TEST_PKI_DIR"
trap 'rm -rf -- "$UDS_TEST_PKI_DIR"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
cd "$UDS_TEST_PKI_DIR"

openssl genrsa -out ca.key 2048
openssl req -x509 -new -key ca.key -sha256 -days 2 -out ca.cer \
  -subj '/C=US/O=UDS Integration Tests/CN=Ephemeral CAC Test CA' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' \
  -addext 'subjectKeyIdentifier=hash'
openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr \
  -config "$repo_root/src/csr.conf" -batch
openssl x509 -req -in client.csr -CA ca.cer -CAkey ca.key \
  -set_serial "0x$(openssl rand -hex 16)" -sha256 -days 1 \
  -out client.cer -extensions v3_ext -extfile "$repo_root/src/csr.conf"
# Cypress expects a path to a passphrase file. Keep its contents out of command lines.
openssl rand -hex 32 | tr -d '\n' > pfx_passphrase.txt
openssl pkcs12 -export -out test.pfx -inkey client.key -in client.cer \
  -certfile ca.cer -passout file:pfx_passphrase.txt
zip -q authorized_certs.zip ca.cer
checksum="$(shasum -a 256 authorized_certs.zip)"
printf '%s\n' "${checksum%% *}" > authorized_certs.zip.sha256
printf 'tls:\n  cacert: "%s"\n' "$(openssl base64 -A -in ca.cer)" > tls_cacert.yaml
openssl verify -CAfile ca.cer client.cer
# Consumers require this marker so a failed generator cannot supply partial outputs.
touch .complete
trap - EXIT
