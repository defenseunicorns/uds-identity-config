<!doctype html>
<html lang="${locale}" class="pf-v5-theme-dark">
<head>
  <meta charset="utf-8">
  <link rel="icon" type="${properties.favIconType!'image/svg+xml'}" href="${resourceUrl}${properties.favIcon!'/favicon.svg'}">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="dark">
  <meta name="description" content="${properties.description!'The Account Console is a web-based interface for managing your account.'}">
  <title>${properties.title!'My Account'}</title>
  <script type="importmap">
    {
      "imports": {
        "react": "${resourceCommonUrl}/vendor/react/react.production.min.js",
          "react/jsx-runtime": "${resourceCommonUrl}/vendor/react/react-jsx-runtime.production.min.js",
          "react-dom": "${resourceCommonUrl}/vendor/react-dom/react-dom.production.min.js"
        }
      }
  </script>
  <script type="module" async blocking="render">
    const DARK_MODE_CLASS = "${properties.kcDarkModeClass}";
    const { classList } = document.documentElement;
    classList.add(DARK_MODE_CLASS);
  </script>
  <#if !isSecureContext>
    <script type="module" src="${resourceCommonUrl}/vendor/web-crypto-shim/web-crypto-shim.js"></script>
  </#if>
  <#if devServerUrl?has_content>
    <script type="module">
      import { injectIntoGlobalHook } from "${devServerUrl}/@react-refresh";

      injectIntoGlobalHook(window);
      window.$RefreshReg$ = () => {};
      window.$RefreshSig$ = () => (type) => type;
    </script>
    <script type="module">
      import { inject } from "${devServerUrl}/@vite-plugin-checker-runtime";

      inject({
        overlayConfig: {},
        base: "/",
      });
    </script>
    <script type="module" src="${devServerUrl}/@vite/client"></script>
    <script type="module" src="${devServerUrl}/src/main.tsx"></script>
  </#if>
  <#if entryStyles?has_content>
    <#list entryStyles as style>
      <link rel="stylesheet" href="${resourceUrl}/${style}">
    </#list>
  </#if>
  <#if properties.styles?has_content>
    <#list properties.styles?split(' ') as style>
      <link rel="stylesheet" href="${resourceUrl}/${style}">
    </#list>
  </#if>
  <#if entryScript?has_content>
    <script type="module" src="${resourceUrl}/${entryScript}"></script>
  </#if>
  <#if properties.scripts?has_content>
    <#list properties.scripts?split(' ') as script>
      <script type="module" src="${resourceUrl}/${script}"></script>
    </#list>
  </#if>
  <#if entryImports?has_content>
    <#list entryImports as import>
      <link rel="modulepreload" href="${resourceUrl}/${import}">
    </#list>
  </#if>
</head>
<body data-page-id="account">
<div id="app">
  <main class="container">
    <div class="keycloak__loading-container">
      <svg class="pf-v5-c-spinner pf-m-xl" role="progressbar" aria-valuetext="Loading..." viewBox="0 0 100 100" aria-label="Contents">
        <circle class="pf-v5-c-spinner__path" cx="50" cy="50" r="45" fill="none"></circle>
      </svg>
      <div>
        <p id="loading-text">Loading the Account Console</p>
      </div>
    </div>
  </main>
</div>
<noscript>JavaScript is required to use the Account Console.</noscript>
<script id="environment" type="application/json">
  {
    "serverBaseUrl": "${serverBaseUrl}",
        "authUrl": "${authUrl}",
        "authServerUrl": "${authServerUrl}",
        "realm": "${realm.name}",
        "clientId": "${clientId}",
        "resourceUrl": "${resourceUrl}",
        "logo": "${properties.logo!""}",
        "logoUrl": "${properties.logoUrl!""}",
        "baseUrl": "${baseUrl}",
        "locale": "${locale}",
        "referrerName": "${referrerName!""}",
        "referrerUrl": "${referrer_uri!""}",
        "features": {
          "isRegistrationEmailAsUsername": ${realm.registrationEmailAsUsername?c},
          "isEditUserNameAllowed": ${realm.editUsernameAllowed?c},
          "isInternationalizationEnabled": ${realm.isInternationalizationEnabled()?c},
          "isLinkedAccountsEnabled": ${isLinkedAccountsEnabled?c},
          "isMyResourcesEnabled": ${(realm.userManagedAccessAllowed && isAuthorizationEnabled)?c},
          "isViewOrganizationsEnabled": ${isViewOrganizationsEnabled?c},
          "deleteAccountAllowed": ${deleteAccountAllowed?c},
          "updateEmailFeatureEnabled": ${updateEmailFeatureEnabled?c},
          "updateEmailActionEnabled": ${updateEmailActionEnabled?c},
          "isViewGroupsEnabled": ${isViewGroupsEnabled?c},
          "isOid4VciEnabled": ${isOid4VciEnabled?c}
  },
  "scope": "${scope!""}"
      }
</script>
</body>
</html>
