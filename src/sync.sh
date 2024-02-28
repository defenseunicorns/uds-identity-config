#!/bin/sh

echo "Syncing customizations to Keycloak"

# Ensure the import directory exists
mkdir -p /opt/keycloak/data/import/
mkdir -p /opt/keycloak/conf/

# Copy the files to their respective directories
cp -fvu realm.json /opt/keycloak/data/import/realm.json
cp -fvur theme /opt/keycloak/themes/theme
cp -fvu *.jar /opt/keycloak/providers/
cp -fvu truststore.jks /opt/keycloak/conf/truststore.jks

echo "Sync complete"
