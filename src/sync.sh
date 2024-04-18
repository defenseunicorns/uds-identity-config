#!/bin/sh
set -e

echo "Syncing customizations to Keycloak"

# Ensure the import directory exists
mkdir -p /opt/keycloak/data/import/
mkdir -p /opt/keycloak/conf/
mkdir -p /opt/keycloak/conf/truststores
mkdir -p /opt/keycloak/themes/theme/

# Copy the files to their respective directories
cp -fvu realm.json /opt/keycloak/data/import/realm.json
cp -fvur theme/* /opt/keycloak/themes/theme/
cp -fvu *.jar /opt/keycloak/providers/
cp -fvu certs/* /opt/keycloak/conf/truststores

echo "Sync complete"
