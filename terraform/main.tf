provider "keycloak" {
  client_id     = var.keycloak_admin_client_id
  url           = var.keycloak_admin_url
  username      = var.keycloak_admin_username
  password      = var.keycloak_admin_password
}

# Get the master realm definition
data "keycloak_realm" "master" {
  realm = "master"
}

# Create Keycloak service account client for using Terraform
resource "keycloak_openid_client" "terraform_client" {
  realm_id                  = data.keycloak_realm.master.id
  client_id                 = "terraform-client"
  name                      = "Terraform Client"
  description               = "Terraform client for configuring service account authorization."
  enabled                   = true
  access_type               = "CONFIDENTIAL"
  valid_redirect_uris       = []
  web_origins               = []
  service_accounts_enabled  = true
}

# Retrieve the terraform client id
data "keycloak_openid_client_service_account_user" "terraform_client_service_account" {
  realm_id  = data.keycloak_realm.master.id
  client_id = keycloak_openid_client.terraform_client.id
}

# Retrieve the realm admin role id
data "keycloak_role" "realm_admin_role" {
  realm_id = data.keycloak_realm.master.id
  name     = "admin"
}

# Assign the realm admin role to the terrafrom service account roles
resource "keycloak_openid_client_service_account_realm_role" "client_service_account_role" {
  realm_id                = data.keycloak_realm.master.id
  service_account_user_id = keycloak_openid_client.terraform_client.service_account_user_id
  role                    = data.keycloak_role.realm_admin_role.name
}

# Configure Azure AD IDP for Master Realm

## Output the alias of the Azure AD IDP for use in the configuration of the authentication flow

# Configure group mapper for Azure AD to Keycloak

# Disable Required Actions
locals {
  required_actions = [
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "CONFIGURE_TOTP",
      name              = "Configure OTP",
      enabled           = false,
      default_action    = false,
      priority          = 10
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "TERMS_AND_CONDITIONS",
      name              = "Terms and Conditions",
      enabled           = false,
      default_action    = false,
      priority          = 20
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "UPDATE_PASSWORD",
      name              = "Update Password",
      enabled           = false,
      default_action    = false,
      priority          = 30
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "UPDATE_PROFILE",
      name              = "Update Profile",
      enabled           = false,
      default_action    = false,
      priority          = 40
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "VERIFY_EMAIL",
      name              = "Verify Email",
      enabled           = false,
      default_action    = false,
      priority          = 50
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "delete_account",
      name              = "Delete Account",
      enabled           = false,
      default_action    = false,
      priority          = 60
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "CONFIGURE_RECOVERY_AUTHN_CODES",
      name              = "Recovery Authentication Codes",
      enabled           = false,
      default_action    = false,
      priority          = 70
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "UPDATE_EMAIL",
      name              = "Update Email",
      enabled           = false,
      default_action    = false,
      priority          = 70
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "webauthn-register",
      name              = "Webauthn Register",
      enabled           = false,
      default_action    = false,
      priority          = 70
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "webauthn-register-passwordless",
      name              = "Webauthn Register Passwordless",
      enabled           = false,
      default_action    = false,
      priority          = 80
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "VERIFY_PROFILE",
      name              = "Verify Profile",
      enabled           = false,
      default_action    = false,
      priority          = 90
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "delete_credential",
      name              = "Delete Credential",
      enabled           = false,
      default_action    = false,
      priority          = 100,
    },
    {
      realm_id          = data.keycloak_realm.master.id
      alias             = "update_user_locale",
      name              = "Update User Locale",
      enabled           = false,
      default_action    = false,
      priority          = 1000
    }
  ]
}

# Loop through required actions
resource "keycloak_required_action" "action" {
  for_each = { for action in local.required_actions : action.alias => action }

  realm_id       = each.value.realm_id
  alias          = each.value.alias
  name           = each.value.name
  enabled        = each.value.enabled
  default_action = each.value.default_action
  priority       = each.value.priority
}

# Configure Authenticaion Flows
resource "keycloak_realm" "master" {
  realm   = data.keycloak_realm.master.id
  enabled = true

  browser_flow = "browser-idp-redirect"
}

# Create new authentication flow
resource "keycloak_authentication_flow" "browser_idp_redirect_flow" {
  realm_id    = data.keycloak_realm.master.id
  alias       = "browser-idp-redirect"
  description = "Browser based authentication with IDP redirect"
  provider_id = "basic-flow"
}

# Create subflow in authentication flow
resource "keycloak_authentication_execution" "idp_redirector" {
  realm_id          = data.keycloak_realm.master.id
  parent_flow_alias = keycloak_authentication_flow.browser_idp_redirect_flow.alias
  authenticator     = "identity-provider-redirector"
  requirement       = "REQUIRED"
}

# Configure the subflow redirector to point to Azure AD IDP alias
resource "keycloak_authentication_execution_config" "saml_idp_config" {
  realm_id     = data.keycloak_realm.master.id
  execution_id = keycloak_authentication_execution.idp_redirector.id
  alias        = "Browser IDP"
  config = {
    "defaultProvider" = var.keycloak_idp_alias
  }
}

# Get temp admin user and output user id for import
data "keycloak_user" "admin_user" {
  realm_id = "master"
  username = "admin"
}

output "admin_user_id" {
  value = data.keycloak_user.admin_user.id
  description = "The ID of the admin user"
}
