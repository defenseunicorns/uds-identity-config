#!/bin/sh
# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

# Build a FIPS-compliant BCFKS system truststore at runtime, inside the uds-config init
# container. Keycloak's upstream TruststoreBuilder hardcodes a non-FIPS PKCS12 truststore
# whenever it has any truststore source, so we build a BCFKS ourselves and Keycloak is
# pointed at it via JAVA_OPTS_APPEND.
#
# Sources, in order:
#   1. DoD CA certs baked into the image at /home/nonroot/certs (from ca-to-jks.sh)
#   2. Every cert found under each entry in $TRUSTSTORE_PATHS. This honors the chart's
#      .Values.truststorePaths and, by default, includes the UDS trust bundle (/tmp/ca-certs)
#      and the Kubernetes service account CA (needed for the Kubernetes identity provider).

set -eu

# These default to their runtime locations but are overridable so the script can be unit-tested
# (see build-truststore.test.sh).
TRUSTSTORE="${TRUSTSTORE:-/opt/keycloak/data/keycloak-truststore.bcfks}"
TRUSTSTORE_PASSWORD="${TRUSTSTORE_PASSWORD:-keycloakchangeit}"
DOD_CERTS_DIR="${DOD_CERTS_DIR:-/home/nonroot/certs}"
BCFIPS_JAR="${BCFIPS_JAR:-$(ls /home/nonroot/fips/libs/bc-fips-*.jar 2>/dev/null | head -1)}"

# Paths to scan for additional CA certs. Defaults to the UDS trust bundle and the Kubernetes
# service account CA; the chart overrides this from .Values.truststorePaths.
TRUSTSTORE_PATHS="${TRUSTSTORE_PATHS:-/tmp/ca-certs /var/run/secrets/kubernetes.io/serviceaccount/ca.crt}"

if [ -z "${BCFIPS_JAR}" ]; then
  echo "ERROR: BC FIPS jar not found under /home/nonroot/fips/libs" >&2
  exit 1
fi

# Rebuild from scratch on every (re)start so the truststore always reflects current sources.
rm -f "${TRUSTSTORE}"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

N=0

# import_one <single-cert file> <alias>
import_one() {
  if keytool -importcert \
       -noprompt \
       -alias "$2" \
       -file "$1" \
       -keystore "${TRUSTSTORE}" \
       -storetype bcfks \
       -providername BCFIPS \
       -providerclass org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider \
       -providerpath "${BCFIPS_JAR}" \
       -storepass "${TRUSTSTORE_PASSWORD}" \
       -trustcacerts >/dev/null 2>&1; then
    N=$((N + 1))
  fi
}

# add_file <file that may contain one or more certs>
add_file() {
  f="$1"
  [ -s "${f}" ] || return 0
  if grep -q "BEGIN CERTIFICATE" "${f}" 2>/dev/null; then
    # Split a (possibly multi-cert) PEM bundle into individual certs. keytool -importcert
    # only reads the first certificate of a bundle, so each one must be imported separately.
    rm -f "${WORK}"/part-* 2>/dev/null || true
    awk 'BEGIN{n=0} /-----BEGIN CERTIFICATE-----/{n++} {print > sprintf("'"${WORK}"'/part-%04d.pem", n)}' "${f}"
    for c in "${WORK}"/part-*; do
      [ -f "${c}" ] || continue
      import_one "${c}" "udsca-${N}"
    done
  else
    # Not PEM: treat as a single (likely DER) certificate.
    import_one "${f}" "udsca-${N}"
  fi
}

# 1) DoD CA certs baked into the image.
if [ -d "${DOD_CERTS_DIR}" ]; then
  for c in "${DOD_CERTS_DIR}"/*; do
    [ -f "${c}" ] && add_file "${c}"
  done
fi

# 2) Cluster trust from truststorePaths (UDS bundle, Kubernetes CA, user-provided paths).
# shellcheck disable=SC2086 # word splitting on the space-separated path list is intentional
for p in ${TRUSTSTORE_PATHS}; do
  if [ -d "${p}" ]; then
    for c in "${p}"/*; do
      [ -f "${c}" ] && add_file "${c}"
    done
  elif [ -f "${p}" ]; then
    add_file "${p}"
  else
    echo "Truststore path not present, skipping: ${p}"
  fi
done

echo "Built BCFKS truststore with ${N} certificate(s): ${TRUSTSTORE}"
