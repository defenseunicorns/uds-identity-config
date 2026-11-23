#!/usr/bin/env bash
# Copyright 2026 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -euo pipefail
umask 077

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixtures="$repo_root/.cache/test-pki"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT
cd "$work_dir"

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
# Keep the existing empty passphrase for these ephemeral test credentials.
: > pfx_passphrase.txt
openssl pkcs12 -export -out test.pfx -inkey client.key -in client.cer \
  -certfile ca.cer -keypbe AES-256-CBC -certpbe AES-256-CBC -passout pass:
zip -q authorized_certs.zip ca.cer
ca_b64="$(openssl base64 -A -in ca.cer)"
printf 'tls:\n  cacert: "%s"\n' "$ca_b64" > tls_cacert.yaml

mkdir -p "$fixtures"
mv authorized_certs.zip tls_cacert.yaml test.pfx pfx_passphrase.txt "$fixtures/"
