#!/bin/sh
# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -e

echo "Syncing customizations to Keycloak"

# Dump environment variables for debugging
# printenv > /opt/keycloak/data/env_dump.txt

# Ensure the import directory exists
mkdir -p /opt/keycloak/data/import/
mkdir -p /opt/keycloak/conf/
mkdir -p /opt/keycloak/conf/truststores
mkdir -p /opt/keycloak/themes/theme/

# Copy the files to their respective directories
cp -fv realm.json /opt/keycloak/data/import/realm.json
cp -fvr theme/* /opt/keycloak/themes/theme/
cp -fv *.jar /opt/keycloak/providers/
cp -fv certs/* /opt/keycloak/conf/truststores

# Check for environment variables and update login theme.properties
{
    echo "# Login Theme configurations"
    echo "REALM_ENABLE_SOCIAL_LOGIN=${REALM_ENABLE_SOCIAL_LOGIN:-true}"
    echo "REALM_ENABLE_X509_LOGIN=${REALM_ENABLE_X509_LOGIN:-true}"
    echo "REALM_ENABLE_USERNAME_PASSWORD_AUTH=${REALM_ENABLE_USERNAME_PASSWORD_AUTH:-true}"
    echo "REALM_ENABLE_REGISTER_BUTTON=${REALM_ENABLE_REGISTER_BUTTON:-true}"
    echo "REALM_ENABLE_REGISTRATION_FIELDS=${REALM_ENABLE_REGISTRATION_FIELDS:-true}"
} >> /opt/keycloak/themes/theme/login/theme.properties

echo "Sync complete"
