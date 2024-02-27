#!/bin/bash

### Grab DoD CAs
X509_CA_BUNDLE="${PWD}/dod_cas.pem"

unzip -q -d /tmp/dod_cas /tmp/dod_cas/dod_cas.zip

find /tmp/dod_cas -name '*.cer' -print0 |
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

find /tmp/dod_cas -type f  -iname '*.cer' -a ! -regex '\(.*EMAIL.*\|.*SW.*\)' -printf "\n" -exec cat {} \; > /tmp/dod_cas/cas.pem
cp /tmp/dod_cas/cas.pem ${X509_CA_BUNDLE}

### Build out Truststore
MAIN_DIR=`pwd`
X509_CRT_DELIMITER="/-----BEGIN CERTIFICATE-----/"
JKS_TRUSTSTORE_FILE="truststore.jks"
JKS_TRUSTSTORE_PATH="${MAIN_DIR}/${JKS_TRUSTSTORE_FILE}"
TRUSTSTORE_PASSWORD="password"
TEMPORARY_CERTIFICATE="temporary_ca.crt"

pushd /tmp >& /dev/null

echo "Creating truststore ${JKS_TRUSTSTORE_PATH}"
cat "${X509_CA_BUNDLE}" > ${TEMPORARY_CERTIFICATE}
csplit -s -z -f crt- "${TEMPORARY_CERTIFICATE}" "${X509_CRT_DELIMITER}" '{*}'
for CERT_FILE in crt-*; do
  # Validate cert is not expired
  if openssl x509 -checkend 0 -noout -in $CERT_FILE &> /dev/null; then
    keytool -import -noprompt -keystore "${JKS_TRUSTSTORE_PATH}" -file "${CERT_FILE}" -storepass "${TRUSTSTORE_PASSWORD}" -alias "service-${CERT_FILE}" >& /dev/null
  fi
done
if [ -f "${JKS_TRUSTSTORE_PATH}" ]; then
  echo "Truststore successfully created at: ${JKS_TRUSTSTORE_PATH}"
else
  echo "ERROR: Creating truststore: ${JKS_TRUSTSTORE_PATH}"
  exit 1
fi

popd >& /dev/null

echo "Validating truststore"
keytool -list -keystore $JKS_TRUSTSTORE_PATH -storepass "${TRUSTSTORE_PASSWORD}" >& /dev/null
if [ $? == 0 ]; then
  echo "Truststore validated"
else
  echo "ERROR: Reading truststore"
  exit 1
fi
