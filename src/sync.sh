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

if [ -d /opt/keycloak/theme-overrides ]; then
    echo "Applying theme customizations"
    if [ -f /opt/keycloak/theme-overrides/logo.svg ]; then
      echo "Overriding logo.svg"
      cp -fv /opt/keycloak/theme-overrides/logo.svg /opt/keycloak/themes/theme/login/resources/img/logo.svg
      cp -fv /opt/keycloak/theme-overrides/logo.svg /opt/keycloak/themes/theme/login/resources/img/uds-logo.svg
      cp -fv /opt/keycloak/theme-overrides/logo.svg /opt/keycloak/themes/theme/account/resources/public/logo.svg
      cp -fv /opt/keycloak/theme-overrides/logo.svg /opt/keycloak/themes/theme/account/resources/public/uds-logo.svg
    fi

    if [ -f /opt/keycloak/theme-overrides/favicon.svg ]; then
      echo "Overriding favicon.svg"
      cp -fv /opt/keycloak/theme-overrides/favicon.svg /opt/keycloak/themes/theme/login/resources/img/favicon.svg
    fi

    if [ -f /opt/keycloak/theme-overrides/background.jpg ]; then
      echo "Overriding background.jpg"
      cp -fv /opt/keycloak/theme-overrides/background.jpg /opt/keycloak/themes/theme/login/resources/img/tech-bg.jpg
      cp -fv /opt/keycloak/theme-overrides/background.jpg /opt/keycloak/themes/theme/account/resources/public/tech-bg.jpg
    fi

    if [ -f /opt/keycloak/theme-overrides/footer.png ]; then
      echo "Overriding footer.png"
      cp -fv /opt/keycloak/theme-overrides/footer.png /opt/keycloak/themes/theme/login/resources/img/full-du-logo.png
      cp -fv /opt/keycloak/theme-overrides/footer.png /opt/keycloak/themes/theme/account/resources/public/full-du-logo.png
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
