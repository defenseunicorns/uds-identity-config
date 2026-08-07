#!/bin/bash
# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -e

# Download DoD CA Certs
X509_CA_BUNDLE="$(pwd)/authorized_certs.pem"
CA_ZIP_URL="${CA_ZIP_URL:-https://dl.dod.cyber.mil/wp-content/uploads/pki-pke/zip/unclass-dod_approved_external_pkis_trust_chains.zip}"
CA_ZIP_ARCHIVE="/tmp/authorized_certs/authorized_certs.zip"
CA_ZIP_SHA256="${CA_ZIP_SHA256:-$(tr -d '[:space:]' <authorized_certs.zip.sha256 2>/dev/null || true)}"

if [ -z "${CA_ZIP_SHA256}" ]; then
  echo "ERROR: CA_ZIP_SHA256 or authorized_certs.zip.sha256 is required" >&2
  exit 1
fi

mkdir -p /tmp/authorized_certs
if [ -f "${CA_ZIP_URL}" ]; then
  cp "${CA_ZIP_URL}" "${CA_ZIP_ARCHIVE}"
else
  curl --fail --show-error --location "${CA_ZIP_URL}" --output "${CA_ZIP_ARCHIVE}"
fi

echo "${CA_ZIP_SHA256}  ${CA_ZIP_ARCHIVE}" | sha256sum --check --strict -

# Extract the archive
unzip -q -d /tmp/authorized_certs "${CA_ZIP_ARCHIVE}"

# Convert all certs to PEM format and remove extra lines
find /tmp/authorized_certs -name '*.cer' -print0 |
while IFS= read -r -d '' cert; do
  if openssl x509 -inform der -in "$cert" -noout 2>/dev/null; then
    echo "Found in Der format: $cert Converting to PEM"
    openssl x509 -inform der -in "$cert" -out "$cert"
  fi
  if ! sed -n '1{/^-----BEGIN CERTIFICATE-----/!q1;}' "$cert" ; then
    echo "Removing extra lines from $cert";
    sed -i -n '/^-----BEGIN CERTIFICATE-----$/,$p' "$cert"
  fi
done

# Combine all certs into a single file, excluding email and software certs
find /tmp/authorized_certs -type f  -iname '*.cer' -a ! -regex "${CA_REGEX_EXCLUSION_FILTER}" -printf "\n" -exec cat {} \; > ${X509_CA_BUNDLE}

# Keycloak 24 and later
CERT_DIR="$(pwd)/certs"
mkdir -p $CERT_DIR

pushd /tmp >& /dev/null

csplit -s -z -f crt- "${X509_CA_BUNDLE}" "/-----BEGIN CERTIFICATE-----/" '{*}'
for CERT_FILE in crt-*; do
  # Validate cert is not expired
  if openssl x509 -checkend 0 -noout -in $CERT_FILE &> /dev/null; then
    echo "Adding $CERT_FILE to truststore"
    cp "${CERT_FILE}" "${CERT_DIR}"
  fi
done

popd >& /dev/null
