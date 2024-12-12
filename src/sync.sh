#!/bin/sh
# Copyright 2024 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -e

echo "Syncing customizations to Keycloak"

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
    echo "ENABLE_SOCIAL_LOGIN=${ENABLE_SOCIAL_LOGIN}"
    echo "ENABLE_X509_LOGIN=${ENABLE_X509_LOGIN}"
    echo "ENABLE_USERNAME_PASSWORD_AUTH=${ENABLE_USERNAME_PASSWORD_AUTH}"
    echo "ENABLE_REGISTER_BUTTON=${ENABLE_REGISTER_BUTTON}"
    echo "REALM_DISABLE_REGISTRATION_FIELDS=${REALM_DISABLE_REGISTRATION_FIELDS:-false}"
    echo "REALM_ENABLE_CUSTOM_BANNER=${REALM_ENABLE_CUSTOM_BANNER:-false}"
    echo "REALM_CUSTOM_BANNER_LEVEL=${REALM_CUSTOM_BANNER_LEVEL:-"UNCLASSIFIED//FOUO"}"
    echo "REALM_CUSTOM_BANNER_BACKGROUND_COLOR=${REALM_CUSTOM_BANNER_BACKGROUND_COLOR:-"#007a33"}"
    echo "REALM_CUSTOM_BANNER_TEXT_COLOR=${REALM_CUSTOM_BANNER_TEXT_COLOR:-"white"}"
} >> /opt/keycloak/themes/theme/login/theme.properties

# Check for environment variables and update account theme.properties
{
    echo "# Account Theme configurations"
    echo "REALM_ENABLE_CUSTOM_BANNER=${REALM_ENABLE_CUSTOM_BANNER:-false}"
    echo "REALM_CUSTOM_BANNER_LEVEL=${REALM_CUSTOM_BANNER_LEVEL:-"UNCLASSIFIED//FOUO"}"
    echo "REALM_CUSTOM_BANNER_BACKGROUND_COLOR=${REALM_CUSTOM_BANNER_BACKGROUND_COLOR:-"#007a33"}"
    echo "REALM_CUSTOM_BANNER_TEXT_COLOR=${REALM_CUSTOM_BANNER_TEXT_COLOR:-"white"}"
} >> /opt/keycloak/themes/theme/account/theme.properties

echo "Sync complete"
