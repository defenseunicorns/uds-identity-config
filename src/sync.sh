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

# if USE_FIPS is true
if [ "${FIPS_ENABLED}" = "true" ]; then
    echo "FIPS mode enabled, copying FIPS libraries"
    cp -fv ./fips/libs/*.jar /opt/keycloak/providers/
fi

if [ -d /opt/keycloak/theme-overrides ]; then
    echo "Applying theme customizations"
    if [ -f /opt/keycloak/theme-overrides/logo.png ]; then
      echo "Overriding logo.png"
      cp -fv /opt/keycloak/theme-overrides/logo.png /opt/keycloak/themes/theme/login/resources/img/logo.png
      cp -fv /opt/keycloak/theme-overrides/logo.png /opt/keycloak/themes/theme/account/resources/public/logo.png
    fi

    if [ -f /opt/keycloak/theme-overrides/favicon.png ]; then
      echo "Overriding favicon.png"
      cp -fv /opt/keycloak/theme-overrides/favicon.png /opt/keycloak/themes/theme/login/resources/img/favicon.png
      cp -fv /opt/keycloak/theme-overrides/favicon.png /opt/keycloak/themes/theme/account/resources/public/favicon.png
    fi

    if [ -f /opt/keycloak/theme-overrides/background.png ]; then
      echo "Overriding background.png"
      cp -fv /opt/keycloak/theme-overrides/background.png /opt/keycloak/themes/theme/login/resources/img/background.png
      cp -fv /opt/keycloak/theme-overrides/background.png /opt/keycloak/themes/theme/account/resources/public/background.png
    fi

    if [ -f /opt/keycloak/theme-overrides/footer.png ]; then
      echo "Overriding footer.png"
      cp -fv /opt/keycloak/theme-overrides/footer.png /opt/keycloak/themes/theme/login/resources/img/footer.png
    fi

    if [ -f /opt/keycloak/theme-overrides/tc.txt ]; then
      echo "Overriding Terms and Conditions"
      echo "# Terms and Conditions" >> /opt/keycloak/themes/theme/login/theme.properties
      key="TC_TEXT"
      value="$(cat /opt/keycloak/theme-overrides/tc.txt)"
      echo "${key}=${value}" >> /opt/keycloak/themes/theme/login/theme.properties
    fi
fi

# Check for environment variables and update login theme.properties
{
    echo "# Login Theme configurations"
    echo "SOCIAL_LOGIN_ENABLED=${SOCIAL_LOGIN_ENABLED}"
    echo "X509_LOGIN_ENABLED=${X509_LOGIN_ENABLED}"
    echo "USERNAME_PASSWORD_AUTH_ENABLED=${USERNAME_PASSWORD_AUTH_ENABLED}"
    echo "REGISTER_BUTTON_ENABLED=${REGISTER_BUTTON_ENABLED}"
    echo "ENABLE_REGISTRATION_FIELDS=${ENABLE_REGISTRATION_FIELDS:-true}"
    echo "WEBAUTHN_ENABLED=${WEBAUTHN_ENABLED}"
    echo "X509_MFA_ENABLED=${X509_MFA_ENABLED}"
} >> /opt/keycloak/themes/theme/login/theme.properties

echo "Sync complete"
