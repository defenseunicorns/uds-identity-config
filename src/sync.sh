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
    echo "ENABLE_SOCIAL_LOGIN=${ENABLE_SOCIAL_LOGIN}"
    echo "ENABLE_X509_LOGIN=${ENABLE_X509_LOGIN}"
    echo "ENABLE_USERNAME_PASSWORD_AUTH=${ENABLE_USERNAME_PASSWORD_AUTH}"
    echo "ENABLE_REGISTER_BUTTON=${ENABLE_REGISTER_BUTTON}"
    echo "REALM_DISABLE_REGISTRATION_FIELDS=${REALM_DISABLE_REGISTRATION_FIELDS:-false}"
} >> /opt/keycloak/themes/theme/login/theme.properties

# Define directory paths
dirs="/opt/keycloak/themes/theme/admin/resources /opt/keycloak/themes/theme/account/resources /opt/keycloak/themes/theme/login/resources"

# Loop through directories and create the custom-banner.js in each
for dir in $dirs; do
  cat <<EOF >"$dir/custom-banner.js"
// custom-banner.js
if ('${REALM_ENABLE_CUSTOM_BANNER}' === "true") {
  document.addEventListener('DOMContentLoaded', function() {
    var appNode = document.getElementById('app');
    var insertTarget = appNode || document.body;
    var isBody = !appNode;

    var topBannerNode = createBannerNode('${REALM_CUSTOM_BANNER_LEVEL}', '${REALM_CUSTOM_BANNER_TEXT_COLOR}', '${REALM_CUSTOM_BANNER_BACKGROUND_COLOR}', 'top', '0');
    if (isBody) {
      document.body.insertBefore(topBannerNode, document.body.firstChild);
    } else {
      document.body.insertBefore(topBannerNode, appNode.nextSibling);
    }
    document.body.style.paddingTop = topBannerNode.offsetHeight + 'px';

    var bottomBannerNode = createBannerNode('${REALM_CUSTOM_BANNER_LEVEL}', '${REALM_CUSTOM_BANNER_TEXT_COLOR}', '${REALM_CUSTOM_BANNER_BACKGROUND_COLOR}', 'bottom', '0');
    document.body.appendChild(bottomBannerNode);
    document.body.style.paddingBottom = bottomBannerNode.offsetHeight + 'px';

    function createBannerNode(text, textColor, bgColor, position, offset) {
      var bannerNode = document.createElement('div');
      bannerNode.className = 'custom-banner';
      bannerNode.textContent = text;
      bannerNode.style.cssText = \`
        width: 100%;
        background-color: \${bgColor};
        color: \${textColor};
        text-align: center;
        position: fixed;
        \${position}: \${offset};
        left: 0;
        z-index: 1000;
      \`;
      return bannerNode;
    }
  });
}
EOF
done

echo "Sync complete"
