#!/bin/sh

echo "Syncing customizations to Keycloak"

# Ensure the import directory exists
mkdir -p /opt/keycloak/data/import/
mkdir -p /opt/keycloak/conf/

# Copy the files to their respective directories
cp -fvu realm.json /opt/keycloak/data/import/realm.json
cp -fvur theme /opt/keycloak/themes/theme
cp -fvu *.jar /opt/keycloak/providers/
# Wrap in some conditional logic based on an ENV, copy a different truststore
if [ ! -z "${CUSTOM_TRUSTSTORE}" ]; then
  cp -fvi "$CUSTOM_TRUSTSTORE" /opt/keycloak/conf/truststore.jks
else
  cp -fvu truststore.jks /opt/keycloak/conf/truststore.jks
fi

echo "Sync complete"
