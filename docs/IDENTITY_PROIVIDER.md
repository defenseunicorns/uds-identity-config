# Keycloak Identity Provider

## Different Keycloak IDP Types Tested
|Name|Description|Working|
|-|-|-|
|Google Social Provider| A social provider meant specifically for google sso integration| Yes |
|OpenId Connect v1.0| A generic openid connection integration| Yes |
|keycloak openid connect| A generic openid connection tailored specifically for keycloak JWT integration| No |
|Keycloak SAML| A SAML support connection [documentation](https://cloud.google.com/architecture/identity/keycloak-single-sign-on) | Yes |

---

# Setting up GCP Project

  1. In [Google Cloud Console](https://console.cloud.google.com)
     1. Create a new project ( top left drop down )
        1. Specifics are up to you here
     2. Select that project ( might take a minute for Google to create it )
        1. Configure `OAuth Consent Screen` ( found by going to the `APIs & Services` menu option )
           1. Select `External`
           2. Hit `Create`
           3. Fill in app name, user support email ( can be changed later ), authorized domain ( uds.dev ), developer contact info
           4. Hit `Save And Continue`
           5. No scopes are necessary ( can be modified later )
           6. No test user is necessary ( can be added later )
           7. Verify and `Back to Dashboard`
        2. Open `Credentials` tab
           1. Click the `Create credentials` option in top nav bar
              1. Select `OAuth client ID`
              2. Select `Web application` type
              3. Add name of client
              4. Add authorized redirect URIs ( based on IDP configuration in keycloak )    Example values:
                 1. OpenID Connect v1.0 = https://sso.uds.dev/realms/uds/broker/{idp name}/endpoint
                 2. Google Social = https://sso.uds.dev/realms/uds/broker/google/endpoint
              5. Hit `Save`
        3. Capture the client ID and client secret for use in setting up IDP ( these can be accessed later )

---

# Configure Keycloak Google SSO

## Configure the Keycloak Realm.json for Automation

Example of using a secret for supplying the configuration values.

1. This section has been added to the realm json

    ```
    "identityProviders": [
        {
          "alias": "oidc",
          "displayName": "Google SSO",
          "internalId": "698ce16e-b026-43d5-8c8c-7977a2659a1c",
          "providerId": "oidc",
          "enabled": "${UDS_GOOGLE_SSO_ENABLED}",
          "updateProfileFirstLoginMode": "on",
          "trustEmail": true,
          "storeToken": false,
          "addReadTokenRoleOnCreate": false,
          "authenticateByDefault": false,
          "linkOnly": false,
          "config": {
            "tokenUrl": "https://oauth2.googleapis.com/token",
            "acceptsPromptNoneForwardFromClient": "false",
            "jwksUrl": "https://www.googleapis.com/oauth2/v3/certs",
            "isAccessTokenJWT": "false",
            "filteredByClaim": "false",
            "backchannelSupported": "false",
            "issuer": "https://accounts.google.com",
            "loginHint": "false",
            "clientAuthMethod": "client_secret_post",
            "syncMode": "IMPORT",
            "clientSecret": "${UDS_GOOGLE_SSO_CLIENT_SECRET}",
            "allowedClockSkew": "0",
            "defaultScope": "openid profile email",
            "userInfoUrl": "https://openidconnect.googleapis.com/v1/userinfo",
            "validateSignature": "true",
            "hideOnLoginPage": "false",
            "clientId": "${UDS_GOOGLE_SSO_CLIENT_ID}",
            "uiLocales": "false",
            "disableNonce": "false",
            "useJwksUrl": "true",
            "sendClientIdOnLogout": "false",
            "pkceEnabled": "false",
            "metadataDescriptorUrl": "https://accounts.google.com/.well-known/openid-configuration",
            "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth",
            "disableUserInfo": "false",
            "sendIdTokenOnLogout": "true",
            "passMaxAge": "false"
          }
        }
    ],
    ```
    * note the `${UDS_GOOGLE_SSO_CLIENT_ID}`, `${UDS_GOOGLE_SSO_ENABLED}`, and `${UDS_GOOGLE_SSO_CLEINT_SECRET}` pieces that are provided by uds-core environment variables

2. Update `uds-core` 
   1. `uds-core/src/keycloak/chart/templates/secret-kc-realm.yaml`

        ```
        apiVersion: v1
        kind: Secret
        metadata:
        name: {{ include "keycloak.fullname" . }}-realm-env
        namespace: {{ .Release.Namespace }}  
        labels:
            {{- include "keycloak.labels" . | nindent 4 }}
        type: Opaque
        data:
        UDS_GOOGLE_SSO_ENABLED: {{ .Values.google.sso.enabled | toString | b64enc }}
        UDS_GOOGLE_SSO_CLIENT_ID: {{ .Values.google.sso.client_id | b64enc }}
        UDS_GOOGLE_SSO_CLIENT_SECRET: {{ .Values.google.sso.client_secret | b64enc }}
        ```

   2. `uds-core/src/keycloak/chart/values.yaml`

        ```
        # Google SSO Values
        google:
        sso:
            enabled: "false"
            client_id: ""
            client_secret: ""
        ```
        * This defines the default values to be used by the IDP, if left unchanged a Google SSO IDP will be created however it will not work as it is disabled and has no client details provided.

   3. `uds-core/src/keycloak/chart/templates/statefulset.yaml`

        ```
          envFrom:
            - secretRef:
                name: {{ include "keycloak.fullname" . }}-realm-env
        ```

## Override default SSO values
`uds-identity-config/bundles/uds-bundle.yaml`

        ```
            overrides:
            keycloak:
                keycloak:
                variables:
                    - name: KEYCLOAK_CONFIG_IMAGE
                        description: "The keycloak config image to deploy plugin and initial setup configuration"
                        path: configImage
                    - name: GOOGLE_SSO_ENABLED
                        description: "Enable Google SSO IDP"
                        path: google.sso.enabled
                        default: "true"
                    - name: GOOGLE_SSO_CLIENT_ID
                        description: "Set Google SSO Client ID"
                        path: google.sso.client_id
                        default: "{fill in value here}"
                    - name: GOOGLE_SSO_CLIENT_Secret
                        description: "Set Google SSO Client Secret"
                        path: google.sso.client_secret
                        default: "{fill in value here}"
        ```


## Manually Setup Google Cloud Project with Keycloak

  1. Create IDP in Keycloak, whether it's oidc or google social provider
     1. For Generic OIDC:
        1. Select `OpenID Connect v1.0`
        2. Add Display Name ( this is what will be displayed on the signin page ): ex. `Google SSO`
        3. Add discovery endpoint: `https://accounts.google.com/.well-known/openid-configuration`
        4. Add client id, this is found in google cloud project from earlier in this doc.
        5. Add client secret, this is found in google cloud project from earlier in this doc. 
        6. Hit `Add`
        7. Update scopes ( under advanced drop down ): `openid profile email` 
            * if adding scopes, probably will need to update scopes in the google client project as well
        8. Toggle `Trust Email` to true
        9. Hit `Save`

     2. For Social Provider:
        1. Select `Google` under the social section
        2. Add client id, this is found in google cloud project from earlier in this doc. 
        3. Add client secret, this is found in google cloud project from earlier in this doc. 
        4. Hit `Save`
        5. Toggle `Trust Email`
        6. Hit `Save`

---