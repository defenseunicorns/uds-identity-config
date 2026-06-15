#!/bin/bash
# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

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

# Build a BCFKS truststore from the validated certs using the BouncyCastle FIPS provider.
# keytool's -providerpath/-providerclass flags are unreliable in Java 17+ for dynamically
# loading BC FIPS. Use a small Java program instead, which calls Security.insertProviderAt()
# directly — the only reliable way to register BC FIPS without pre-configuring the JDK.
TRUSTSTORE="$(pwd)/keycloak-truststore.bcfks"
TRUSTSTORE_PASSWORD="keycloakchangeit"
BCFIPS_JAR=$(ls /home/build/fips-libs/bc-fips-*.jar | head -1)

cat > /tmp/CreateBCFKSTruststore.java << 'JAVA'
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.Collection;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;

public class CreateBCFKSTruststore {
    public static void main(String[] args) throws Exception {
        String keystoreFile = args[0];
        char[] password = args[1].toCharArray();
        Security.insertProviderAt(new BouncyCastleFipsProvider(), 1);
        KeyStore ks = KeyStore.getInstance("BCFKS", "BCFIPS");
        ks.load(null, password);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        int n = 0;
        for (int i = 2; i < args.length; i++) {
            try (FileInputStream fis = new FileInputStream(args[i])) {
                for (Certificate cert : cf.generateCertificates(fis)) {
                    ks.setCertificateEntry("udsca-" + n, cert);
                    n++;
                }
            } catch (Exception e) {
                // skip unparseable cert files
            }
        }
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            ks.store(fos, password);
        }
        System.out.println("Imported " + n + " certificate(s) into " + keystoreFile);
    }
}
JAVA

javac -cp "$BCFIPS_JAR" /tmp/CreateBCFKSTruststore.java -d /tmp/
java -cp "/tmp:$BCFIPS_JAR" CreateBCFKSTruststore "$TRUSTSTORE" "$TRUSTSTORE_PASSWORD" "${CERT_DIR}"/*
