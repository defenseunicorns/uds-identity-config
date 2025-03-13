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
    echo "SOCIAL_LOGIN_ENABLED=${SOCIAL_LOGIN_ENABLED}"
    echo "X509_LOGIN_ENABLED=${X509_LOGIN_ENABLED}"
    echo "USERNAME_PASSWORD_AUTH_ENABLED=${USERNAME_PASSWORD_AUTH_ENABLED}"
    echo "REGISTER_BUTTON_ENABLED=${REGISTER_BUTTON_ENABLED}"
    echo "REALM_DISABLE_REGISTRATION_FIELDS=${REALM_DISABLE_REGISTRATION_FIELDS:-false}"
    echo "WEBAUTHN_ENABLED=${WEBAUTHN_ENABLED}"
    echo "X509_MFA_ENABLED=${X509_MFA_ENABLED}"
} >> /opt/keycloak/themes/theme/login/theme.properties

echo "Sync complete"
