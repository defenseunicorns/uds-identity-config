#!/usr/bin/env bash
# Copyright 2026 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.cache/test-pki"
work_dir="$(mktemp -d validate.XXXXXXXX)"
if [[ ! "$work_dir" =~ ^validate\.[[:alnum:]]{8}$ ]]; then
  echo 'Unexpected validator scratch directory' >&2
  exit 1
fi
readonly work_dir
trap 'rm -rf -- "$work_dir"' EXIT

if [[ ! -f pfx_passphrase.txt || -s pfx_passphrase.txt ]]; then
  echo 'Expected an empty pfx_passphrase.txt for Cypress' >&2
  exit 1
fi

# Docker, the gateways, and the PFX must carry the same CA.
if [[ "$(unzip -Z1 authorized_certs.zip)" != ca.cer ]]; then
  echo 'Expected authorized_certs.zip to contain only ca.cer' >&2
  exit 1
fi
unzip -p authorized_certs.zip ca.cer > "$work_dir/ca.cer"
yq -er '.tls.cacert' tls_cacert.yaml | openssl base64 -d -A | cmp - "$work_dir/ca.cer"
openssl pkcs12 -in test.pfx -passin pass: -cacerts -nokeys | openssl x509 | cmp - "$work_dir/ca.cer"

openssl pkcs12 -in test.pfx -passin pass: -clcerts -nokeys | openssl x509 > "$work_dir/client.cer"
openssl verify -purpose sslclient -CAfile "$work_dir/ca.cer" "$work_dir/client.cer"
# Use the same TLS runtime as Cypress to check the packaged client identity.
node -e "require('node:tls').createSecureContext({pfx: require('node:fs').readFileSync('test.pfx'), passphrase: ''})"

subject="$(openssl x509 -in "$work_dir/client.cer" -noout -subject -nameopt RFC2253 | sed 's/^subject= *//')"
if [[ "$subject" != 'CN=UNICORN.DOUG.ROCKSTAR.1234567890,OU=PKI,O=U.S. Government,C=US' ]]; then
  echo "Unexpected CAC test identity: $subject" >&2
  exit 1
fi

# LibreSSL omits otherName values from its human-readable SAN output.
san_offset="$(openssl asn1parse -in "$work_dir/client.cer" | awk -F: '/:X509v3 Subject Alternative Name$/ {getline; print $1 + 0}')"
openssl asn1parse -in "$work_dir/client.cer" -strparse "$san_offset" > "$work_dir/san"
grep -Eq 'OBJECT +:2\.16\.840\.1\.101\.3\.6\.6$' "$work_dir/san"
grep -Eq 'UTF8STRING +:test@mil$' "$work_dir/san"

openssl x509 -in "$work_dir/client.cer" -noout -text | tr ',' '\n' | sed 's/^[[:space:]]*//' > "$work_dir/attributes"
for value in 'Digital Signature' 'TLS Web Client Authentication' '2.16.840.1.101.3.6.8' \
  'Policy: 2.16.840.1.101.3.2.1.3.12' 'email:doug.unicorn@uds.dev'; do
  if ! grep -Fxq -- "$value" "$work_dir/attributes"; then
    echo "Missing CAC certificate attribute: $value" >&2
    exit 1
  fi
done
