#!/bin/bash
set -e

# Download DoD CA Certs
X509_CA_BUNDLE="$(pwd)/authorized_certs.pem"

# Extract the archive
unzip -q -d /tmp/authorized_certs /tmp/authorized_certs/authorized_certs.zip

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

# Keycloak 23
# Create a truststore and import the certs
JKS_TRUSTSTORE_PATH="$(pwd)/truststore.jks"
# Using the Keycloak default because we are only storing public certs
TRUSTSTORE_PASSWORD="password"

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

    keytool -import -noprompt -keystore "${JKS_TRUSTSTORE_PATH}" -file "${CERT_FILE}" -storepass "${TRUSTSTORE_PASSWORD}" -alias "service-${CERT_FILE}" >& /dev/null
  fi
done

popd >& /dev/null

keytool -list -keystore $JKS_TRUSTSTORE_PATH -storepass "${TRUSTSTORE_PASSWORD}"
if [ $? == 0 ]; then
  echo "Truststore validated"
else
  echo "ERROR: Reading truststore"
  exit 1
fi
