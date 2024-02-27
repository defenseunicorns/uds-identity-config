# Build the Keycloak java plugin
FROM cgr.dev/chainguard/maven:openjdk-17 as plugin

WORKDIR /home/build

COPY plugin/pom.xml .
COPY plugin/src ./src

RUN mvn clean package

# Build the Java truststore from DOD CAs
FROM cgr.dev/chainguard/jdk:latest-dev as truststore
USER root
RUN apk add openssl coreutils sed bash findutils

WORKDIR /home/build

ADD https://dl.dod.cyber.mil/wp-content/uploads/pki-pke/zip/unclass-dod_approved_external_pkis_trust_chains.zip /tmp/dod_cas/dod_cas.zip
COPY truststore/ca-to-jks.sh .

RUN ./ca-to-jks.sh

# Use busybox as the base image to perform the sync
FROM cgr.dev/chainguard/busybox:latest

WORKDIR /home/nonroot

# Copy the built plugin
COPY --chown=nonroot --from=plugin /home/build/target/*.jar .

# Copy additional JARs to this directory to have Keycloak load them on startup

# Copy the DOD Unclass truststore for Keycloak Auth
COPY --chown=nonroot --from=truststore /home/build/truststore.jks .

# The realm.json is loaded into Keycloak on startup only if the realm does not exist
COPY --chown=nonroot realm.json .

# This provides the custom theme for the Keycloak instance
COPY --chown=nonroot theme theme

# This script is used to sync the files from the init container to the Keycloak container
COPY --chown=nonroot sync.sh .
RUN chmod +x sync.sh

CMD ["/home/nonroot/sync.sh"]
