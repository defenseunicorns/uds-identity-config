#!/usr/bin/env bash
# Copyright 2026 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -euo pipefail
umask 022
cd "$(dirname "${BASH_SOURCE[0]}")/.."
mkdir -p .cache/test-pki
work_dir="$(mktemp -d "$PWD/.cache/test-pki/validation.XXXXXX")"
trap 'rm -rf -- "$work_dir"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Independent concurrent generations must produce fresh CAs.
export UDS_TEST_PKI_DIR="$work_dir/first run"
bash scripts/generate-test-pki.sh &
first_pid=$!
UDS_TEST_PKI_DIR="$work_dir/second run" bash scripts/generate-test-pki.sh &
second_pid=$!
wait "$first_pid"
wait "$second_pid"
if cmp -s "$UDS_TEST_PKI_DIR/ca.cer" "$work_dir/second run/ca.cer"; then
  echo 'Independent generations must use different CAs' >&2
  exit 1
fi
original_ca="$(shasum -a 256 "$UDS_TEST_PKI_DIR/ca.cer")"
if bash scripts/generate-test-pki.sh 2>/dev/null; then
  echo 'Generation must refuse to replace an existing PKI' >&2
  exit 1
fi

[[ "$original_ca" == "$(shasum -a 256 "$UDS_TEST_PKI_DIR/ca.cer")" ]]
pki="$UDS_TEST_PKI_DIR"
[[ ! -s "$pki/pfx_passphrase.txt" ]]
openssl verify -purpose sslclient -CAfile "$pki/ca.cer" "$pki/client.cer"
subject="$(openssl x509 -in "$pki/client.cer" -noout -subject -nameopt RFC2253)"
[[ "$subject" == 'subject=CN=UNICORN.DOUG.ROCKSTAR.1234567890,OU=PKI,O=U.S. Government,C=US' ]]
cert_text="$(openssl x509 -in "$pki/client.cer" -noout -text)"
for value in 'TLS Web Client Authentication' 'Digital Signature' \
  '2.16.840.1.101.3.6.8' '2.16.840.1.101.3.2.1.3.12' \
  '2.16.840.1.101.3.6.6' 'test@mil' 'email:doug.unicorn@uds.dev'; do
  [[ "$cert_text" == *"$value"* ]]
done

# Extract only public material from the PFX, including the key's public half.
openssl pkcs12 -in "$pki/test.pfx" -passin pass: \
  -nocerts -nodes | openssl pkey -pubout > "$work_dir/key.pub"
openssl x509 -in "$pki/client.cer" -pubkey -noout > "$work_dir/cert.pub"
cmp "$work_dir/key.pub" "$work_dir/cert.pub"
openssl pkcs12 -in "$pki/test.pfx" -passin pass: \
  -clcerts -nokeys | openssl x509 | cmp - "$pki/client.cer"
openssl pkcs12 -in "$pki/test.pfx" -passin pass: \
  -cacerts -nokeys | openssl x509 | cmp - "$pki/ca.cer"
unzip -p "$pki/authorized_certs.zip" ca.cer | cmp - "$pki/ca.cer"
yq -r '.tls.cacert' "$pki/tls_cacert.yaml" | openssl base64 -d -A | cmp - "$pki/ca.cer"
checksum="$(shasum -a 256 "$pki/authorized_certs.zip")"
[[ "${checksum%% *}" == "$(cat "$pki/authorized_certs.zip.sha256")" ]]
[[ -z "$(find "$pki" -type f ! -perm 600)" ]]
[[ -z "$(find "$pki" -type d ! -perm 700)" ]]
git check-ignore -q "$pki/test.pfx"
[[ -z "$(git status --porcelain --untracked-files=all -- "$work_dir")" ]]
echo 'PKI chain, identity, PFX key, shared CA, checksum, permissions, and fresh generation verified.'
