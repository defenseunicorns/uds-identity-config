#!/bin/sh
# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

# Unit tests for build-truststore.sh. Designed to run INSIDE the identity-config image
# (which provides keytool and the BouncyCastle FIPS jar), e.g.:
#   docker run --rm -v "$PWD/src/truststore/build-truststore.test.sh:/test.sh:ro" \
#     --entrypoint sh uds-core-config:keycloak /test.sh
# The script under test is exercised via its real shipped copy at /home/nonroot/build-truststore.sh,
# with its TRUSTSTORE / DOD_CERTS_DIR / TRUSTSTORE_PATHS inputs overridden through the environment.

set -u

SCRIPT="${SCRIPT:-/home/nonroot/build-truststore.sh}"
BCFIPS_JAR="${BCFIPS_JAR:-$(ls /home/nonroot/fips/libs/bc-fips-*.jar 2>/dev/null | head -1)}"
PASSWORD="keycloakchangeit"

if [ ! -f "${SCRIPT}" ]; then echo "FATAL: script under test not found: ${SCRIPT}"; exit 1; fi
if [ -z "${BCFIPS_JAR}" ]; then echo "FATAL: BC FIPS jar not found"; exit 1; fi

ROOT="$(mktemp -d)"
trap 'rm -rf "${ROOT}"' EXIT
PASS=0
FAIL=0

pass() { echo "  PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $1"; FAIL=$((FAIL + 1)); }

# mkcert <out.pem> <CN>: generate a self-signed cert PEM using keytool (no openssl needed).
mkcert() {
  ks="$(mktemp -u)"
  keytool -genkeypair -alias x -keyalg RSA -keysize 2048 -dname "CN=$2" -validity 2 \
    -keystore "${ks}" -storetype pkcs12 -storepass changeit -keypass changeit >/dev/null 2>&1
  keytool -exportcert -rfc -alias x -keystore "${ks}" -storepass changeit -file "$1" >/dev/null 2>&1
  rm -f "${ks}"
}

# entries <bcfks-file>: number of trustedCertEntry entries (0 if the file is missing/unreadable).
entries() {
  [ -f "$1" ] || { echo 0; return; }
  keytool -list -keystore "$1" -storetype bcfks -providername BCFIPS \
    -providerclass org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider \
    -providerpath "${BCFIPS_JAR}" -storepass "${PASSWORD}" 2>/dev/null | grep -c "trustedCertEntry"
}

# is_bcfks <bcfks-file>: succeed only if the keystore reports type BCFKS.
is_bcfks() {
  keytool -list -keystore "$1" -storetype bcfks -providername BCFIPS \
    -providerclass org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider \
    -providerpath "${BCFIPS_JAR}" -storepass "${PASSWORD}" 2>/dev/null | grep -q "Keystore type: BCFKS"
}

# run_build <truststore-out> <dod-dir> <truststore-paths>
run_build() {
  out="$1"; dod="$2"; paths="$3"
  rm -f "${out}"
  TRUSTSTORE="${out}" TRUSTSTORE_PASSWORD="${PASSWORD}" DOD_CERTS_DIR="${dod}" \
    BCFIPS_JAR="${BCFIPS_JAR}" TRUSTSTORE_PATHS="${paths}" sh "${SCRIPT}" >/dev/null 2>&1
}

echo "Testing ${SCRIPT}"

# --- Test 1: DoD certs + multi-cert UDS bundle + Kubernetes CA are all imported as BCFKS ---
echo "Test 1: imports DoD + multi-cert bundle + k8s CA into a BCFKS truststore"
T="${ROOT}/t1"; mkdir -p "${T}/dod" "${T}/cacerts" "${T}/sa"
mkcert "${T}/dod/crt-01" "dod-root"
mkcert "${T}/c1.pem" "uds-a"; mkcert "${T}/c2.pem" "uds-b"
cat "${T}/c1.pem" "${T}/c2.pem" > "${T}/cacerts/extra.pem"   # multi-cert bundle
mkcert "${T}/sa/ca.crt" "kube-ca"
run_build "${T}/out.bcfks" "${T}/dod" "${T}/cacerts ${T}/sa/ca.crt"
is_bcfks "${T}/out.bcfks" && pass "keystore type is BCFKS" || fail "keystore type is not BCFKS"
n="$(entries "${T}/out.bcfks")"
[ "${n}" -eq 4 ] && pass "imported 4 certs (1 DoD + 2 bundle + 1 k8s CA)" || fail "expected 4 entries, got ${n}"

# --- Test 2: a multi-cert PEM bundle is split so every cert is imported (not just the first) ---
echo "Test 2: splits a multi-cert PEM bundle into individual entries"
T="${ROOT}/t2"; mkdir -p "${T}/dod" "${T}/cacerts"
mkcert "${T}/a.pem" "a"; mkcert "${T}/b.pem" "b"; mkcert "${T}/c.pem" "c"
cat "${T}/a.pem" "${T}/b.pem" "${T}/c.pem" > "${T}/cacerts/bundle.pem"
run_build "${T}/out.bcfks" "${T}/dod" "${T}/cacerts"
n="$(entries "${T}/out.bcfks")"
[ "${n}" -eq 3 ] && pass "split 3-cert bundle into 3 entries" || fail "expected 3 entries, got ${n}"

# --- Test 3: a non-existent truststore path is skipped without failing the build ---
echo "Test 3: skips missing truststore paths gracefully"
T="${ROOT}/t3"; mkdir -p "${T}/dod"
mkcert "${T}/dod/crt-01" "dod-root"
run_build "${T}/out.bcfks" "${T}/dod" "${T}/does-not-exist /also/missing"
rc=$?
[ "${rc}" -eq 0 ] && pass "build exited 0 with only missing paths" || fail "build exited ${rc}"
n="$(entries "${T}/out.bcfks")"
[ "${n}" -eq 1 ] && pass "only the DoD cert was imported" || fail "expected 1 entry, got ${n}"

# --- Test 4: builds from DoD certs alone when no extra paths resolve ---
echo "Test 4: builds from DoD certs only"
T="${ROOT}/t4"; mkdir -p "${T}/dod"
mkcert "${T}/dod/crt-01" "dod-1"; mkcert "${T}/dod/crt-02" "dod-2"
run_build "${T}/out.bcfks" "${T}/dod" ""
n="$(entries "${T}/out.bcfks")"
[ "${n}" -eq 2 ] && pass "imported 2 DoD certs" || fail "expected 2 entries, got ${n}"

echo ""
echo "Results: ${PASS} passed, ${FAIL} failed"
[ "${FAIL}" -eq 0 ]
