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
# Use busybox as the base image to perform the sync                               #
#                                                                                 #
###################################################################################
FROM cgr.dev/chainguard/busybox:latest

WORKDIR /home/nonroot

# Copy Jars from the plugin build stage
COPY --chown=nonroot --from=plugin /home/build/target/*.jar .

# FIPS dependencies
RUN mkdir -p ./fips/libs
RUN chown -R nonroot:nonroot ./fips/libs
COPY --chown=nonroot --from=plugin /home/build/target/fips/libs/*.jar ./fips/libs

# Copy the DOD Unclass PEM and truststore for Istio & Keycloak Auth
COPY --chown=nonroot --from=truststore /home/build/authorized_certs.pem .
COPY --chown=nonroot --from=truststore /home/build/certs ./certs

# The realm.json is loaded into Keycloak on startup only if the realm does not exist
ARG REALM_FILE=realm.json
COPY --chown=nonroot ${REALM_FILE} .

# This provides the custom theme for the Keycloak instance
COPY --chown=nonroot theme theme

# This script is used to sync the files from the init container to the Keycloak container
COPY --chown=nonroot sync.sh .
RUN chmod +x sync.sh

CMD ["/home/nonroot/sync.sh"]
