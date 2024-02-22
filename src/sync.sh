#!/bin/sh

echo "Syncing customizations to Keycloak"

# Ensure the import directory exists
mkdir -p /opt/keycloak/data/import/

# Copy the files to their respective directories
cp -fvu realm.json /opt/keycloak/data/import/realm.json
cp -fvur theme /opt/keycloak/themes/theme
cp -fvu *.jar /opt/keycloak/providers/

echo "Sync complete"
