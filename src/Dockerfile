# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

###################################################################################
#                                                                                 #
# Build the Keycloak java plugin and copy any extra-jars                          #
#                                                                                 #
###################################################################################
FROM maven:3-eclipse-temurin-21-jammy AS plugin

WORKDIR /home/build/plugin

COPY plugin/pom.xml .
RUN mvn dependency:go-offline
COPY plugin/src ./src
RUN mkdir -p ../truststore
COPY truststore/build_bcfjks.sh ../truststore/build_bcfjks.sh

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
# This stage only validates and splits the DoD CA bundle (openssl + unzip); the BCFKS
# truststore itself is built at runtime in the uds-config init container (build_bcfjks.sh).
FROM registry.access.redhat.com/ubi9/openjdk-21 AS truststore
USER root
RUN microdnf install -y unzip openssl

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
# Chainguard JDK base: runs sync.sh and builds the BCFKS truststore at runtime.    #
# A JDK is required because build_bcfjks.sh uses keytool with the BouncyCastle     #
# FIPS provider to assemble the BCFKS truststore when the init container starts.   #
#                                                                                 #
###################################################################################
FROM cgr.dev/chainguard/jdk:latest

# The Chainguard JDK image's nonroot user is uid/gid 65532 (named "java"); the chart runs
# this init container as runAsUser 65532. Files are copied with that ownership so they are
# readable at runtime. USER root is only needed so the build can create directories under
# /home/nonroot before switching back to the nonroot uid.
USER root
WORKDIR /home/nonroot

# Copy Jars from the plugin build stage
COPY --chown=65532:65532 --from=plugin /home/build/plugin/target/*.jar .

# FIPS dependencies (also used at runtime by build_bcfjks.sh via -providerpath)
RUN mkdir -p ./fips/libs && chown -R 65532:65532 ./fips/libs
COPY --chown=65532:65532 --from=plugin /home/build/plugin/target/fips/libs/*.jar ./fips/libs

# Copy the DOD Unclass PEM and individual certs for Istio & Keycloak Auth
COPY --chown=65532:65532 --from=truststore /home/build/authorized_certs.pem .
COPY --chown=65532:65532 --from=truststore /home/build/certs ./certs

# The realm.json is loaded into Keycloak on startup only if the realm does not exist
COPY --chown=65532:65532 --from=json5-converter /convert/realm.json .

# This provides the custom theme for the Keycloak instance
COPY --chown=65532:65532 theme theme

# sync.sh syncs files into the Keycloak container; build_bcfjks.sh builds the BCFKS
# system truststore at runtime.
COPY --chown=65532:65532 sync.sh .
COPY --chown=65532:65532 --from=plugin /home/build/plugin/target/test-classes/truststore/build_bcfjks.sh .
RUN chmod +x sync.sh build_bcfjks.sh

USER 65532
ENTRYPOINT ["/home/nonroot/sync.sh"]
