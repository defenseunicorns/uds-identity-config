# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

###################################################################################
#                                                                                 #
# Build the Keycloak java plugin and copy any extra-jars                          #
#                                                                                 #
###################################################################################
FROM maven:3-eclipse-temurin-21-jammy AS plugin

WORKDIR /home/build

COPY plugin/pom.xml .
RUN mvn dependency:go-offline
COPY plugin/src ./src

RUN mvn package

# https://www.keycloak.org/server/fips#_bouncycastle_fips_bits
# Keycloak FIPS mode requires additional libraries. This way we ensure its version is exactly
# the same as use used by Keycloak Server.
RUN mvn dependency:copy-dependencies -DoutputDirectory=target/lib
RUN mkdir -p target/fips/libs
RUN cp target/lib/bc-fips-*.jar target/fips/libs
RUN cp target/lib/bctls-fips-*.jar target/fips/libs
RUN cp target/lib/bcpkix-fips-*.jar target/fips/libs
RUN cp target/lib/bcutil-fips-*.jar target/fips/libs

# Include anything in the extra-jars directory in the target directory
# We will filter out in the next build stage
COPY extra-jars/* ./target/

###################################################################################
#                                                                                 #
# Build the Java truststore from DOD CAs                                          #
#                                                                                 #
###################################################################################
FROM maven:3-eclipse-temurin-21-jammy AS truststore
USER root
RUN apt-get update && apt-get install unzip -y

WORKDIR /home/build

ARG CA_REGEX_EXCLUSION_FILTER="\(.*EMAIL.*\|.*SW.*\)"
ARG CA_ZIP_URL=https://dl.dod.cyber.mil/wp-content/uploads/pki-pke/zip/unclass-dod_approved_external_pkis_trust_chains.zip

# Allow overriding the default regex filter for the CA names
ENV CA_REGEX_EXCLUSION_FILTER=${CA_REGEX_EXCLUSION_FILTER}

ADD ${CA_ZIP_URL} /tmp/authorized_certs/authorized_certs.zip
COPY truststore/ca-to-jks.sh .

RUN ./ca-to-jks.sh

###################################################################################
#                                                                                 #
# Convert JSON5 realm file to JSON                                               #
#                                                                                 #
###################################################################################
FROM node:24-alpine AS json5-converter

ARG REALM_FILE=realm.json5

WORKDIR /convert

# Install json5 CLI tool
RUN npm install -g json5@2.2.3

# Copy the realm file and convert it to JSON if it's JSON5, otherwise just copy
COPY ${REALM_FILE} input_realm_file
RUN if [ "${REALM_FILE##*.}" = "json5" ]; then \
        json5 -c --out-file realm.json input_realm_file; \
    else \
        cp input_realm_file realm.json; \
    fi

###################################################################################
#                                                                                 #
# Pull the keycloak-config-cli jar from the upstream signed image. Tag pins       #
# kcc to a Keycloak compatible build (kcc 6.5.0 against Keycloak 26.5.4 line —    #
# matches our Keycloak 26.5.x within supported window).                           #
#                                                                                 #
###################################################################################
FROM docker.io/adorsys/keycloak-config-cli:6.5.0-26.5.4 AS kcc

###################################################################################
#                                                                                 #
# Use a JRE-capable chainguard base so the image can serve two roles:             #
#   - Default CMD = sync.sh (init container; copies files into Keycloak volumes) #
#   - Job override = `java -jar /opt/kcc/keycloak-config-cli.jar` (realm sync)   #
# ENTRYPOINT is cleared because the upstream chainguard JRE image sets it to     #
# `/usr/bin/java`, which would consume our CMD as a java argument.                #
#                                                                                 #
###################################################################################
FROM cgr.dev/chainguard/jre:latest-dev

USER root
WORKDIR /home/nonroot

# Copy Jars from the plugin build stage
COPY --chown=65532:65532 --from=plugin /home/build/target/*.jar .

# FIPS dependencies
RUN mkdir -p ./fips/libs
RUN chown -R 65532:65532 ./fips/libs
COPY --chown=65532:65532 --from=plugin /home/build/target/fips/libs/*.jar ./fips/libs

# Copy the DOD Unclass PEM and truststore for Istio & Keycloak Auth
COPY --chown=65532:65532 --from=truststore /home/build/authorized_certs.pem .
COPY --chown=65532:65532 --from=truststore /home/build/certs ./certs

# The realm.json is loaded into Keycloak on startup only if the realm does not exist
COPY --chown=65532:65532 --from=json5-converter /convert/realm.json .

# This provides the custom theme for the Keycloak instance
COPY --chown=65532:65532 theme theme

# keycloak-config-cli for realmSync mode. Activated by overriding the Job's
# command/args; default CMD remains sync.sh for init-container usage.
COPY --chown=65532:65532 --from=kcc /app/keycloak-config-cli.jar /opt/kcc/keycloak-config-cli.jar

# realm-kcc.json — curated baseline for kcc realmSync mode. Same realm as
# realm.json but with Keycloak built-ins / auto-managed resources stripped
# and ${VAR:default} placeholders rewritten to ${env:VAR:-default} so kcc
# can substitute natively. See docs/dev/keycloak-config-cli-experiment/
# findings.md (F-1, F-2 through F-9) for derivation.
COPY --chown=65532:65532 realm-kcc.json .

# This script is used to sync the files from the init container to the Keycloak container
COPY --chown=65532:65532 sync.sh .
RUN chmod +x sync.sh

USER 65532:65532
ENTRYPOINT []
CMD ["/home/nonroot/sync.sh"]
