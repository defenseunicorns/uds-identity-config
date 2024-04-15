###################################################################################
#                                                                                 #
# Build the Keycloak java plugin and copy any extra-jars                          #
#                                                                                 #
###################################################################################
FROM cgr.dev/chainguard/maven:openjdk-17 as plugin

WORKDIR /home/build

COPY plugin/pom.xml .
COPY plugin/src ./src

RUN mvn clean package

# Include anything in the extra-jars directory in the target directory
# We will filter out in the next build stage
COPY extra-jars/* ./target/

###################################################################################
#                                                                                 #
# Build the Java truststore from DOD CAs                                          #
#                                                                                 #
###################################################################################
FROM amazoncorretto:21-alpine-jdk as truststore
USER root
RUN apk add openssl coreutils sed bash findutils

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
