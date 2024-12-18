provider "keycloak" {
  client_id     = var.keycloak_admin_client_id
  url           = "https://keycloak.admin.${var.uds_domain}"
  username      = var.keycloak_admin_username
  password      = var.keycloak_admin_password
}

# Generate keycloak terraform client secret
resource "random_password" "terraform_client_secret" {
  length           = 32
  special          = true
  override_special = "_%"
}

# Create Keycloak service account client for using Terraform
resource "keycloak_openid_client" "terraform_client" {
  realm_id                  = keycloak_realm.master.id
  client_id                 = "terraform-client"
  name                      = "Terraform Client"
  description               = "Terraform client for configuring service account authorization."
  enabled                   = true
  access_type               = "CONFIDENTIAL"
  client_secret             = random_password.terraform_client_secret.result
  valid_redirect_uris       = []
  web_origins               = []
  service_accounts_enabled  = true
}

# Retrieve the terraform client id
data "keycloak_openid_client_service_account_user" "terraform_client_service_account" {
  realm_id  = keycloak_realm.master.id
  client_id = keycloak_openid_client.terraform_client.id
}

# Retrieve the realm admin role id
data "keycloak_role" "realm_admin_role" {
  realm_id = keycloak_realm.master.id
  name     = "admin"
}

# Assign the realm admin role to the terrafrom service account roles
resource "keycloak_openid_client_service_account_realm_role" "client_service_account_role" {
  realm_id                = keycloak_realm.master.id
  service_account_user_id = keycloak_openid_client.terraform_client.service_account_user_id
  role                    = data.keycloak_role.realm_admin_role.name
}

# Create Master Realm User Group
resource "keycloak_group" "admin_group" {
  realm_id = keycloak_realm.master.id
  name = "admin-group"
}

# Assign Admin Group the Realm Admin Role
resource "keycloak_group_roles" "group_roles" {
  realm_id = keycloak_realm.master.id
  group_id = keycloak_group.admin_group.id

  role_ids = [
    data.keycloak_role.realm_admin_role.id
  ]
}

resource "keycloak_saml_identity_provider" "realm_azure_saml_identity_provider" {
  realm        = keycloak_realm.master.id
  alias        = var.identity_provider_name
  display_name = "Azure SSO"

  entity_id                  = "api://${azuread_application.keycloak-saml.client_id}"
  single_sign_on_service_url = "https://login.microsoftonline.us/${data.azuread_client_config.current.tenant_id}/saml2"
  single_logout_service_url  = "https://login.microsoftonline.us/${data.azuread_client_config.current.tenant_id}/saml2"


  post_broker_login_flow_alias = "Group Protection Authorization"
  backchannel_supported        = false
  post_binding_response        = true
  post_binding_logout          = false
  post_binding_authn_request   = true
  store_token                  = false
  trust_email                  = true
  force_authn                  = true
  validate_signature           = false
  principal_type               = "SUBJECT"
  want_assertions_encrypted    = false

  sync_mode = "LEGACY"
  extra_config = {
    metadataDescriptorUrl    = "https://login.microsoftonline.us/${data.azuread_client_config.current.tenant_id}/federationmetadata/2007-06/federationmetadata.xml"
    useMetadataDescriptorUrl = true
    idpEntityId              = "https://sts.windows.net/${data.azuread_client_config.current.tenant_id}/"
  }
}

resource "keycloak_attribute_importer_identity_provider_mapper" "username" {
  realm                   = keycloak_realm.master.id
  name                    = "username-attribute-importer"
  claim_name              = "username"
  attribute_name          = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress"
  identity_provider_alias = keycloak_saml_identity_provider.realm_azure_saml_identity_provider.alias
  user_attribute          = "username"
}

resource "keycloak_attribute_importer_identity_provider_mapper" "name" {
  realm                   = keycloak_realm.master.id
  name                    = "email-attribute-importer"
  claim_name              = "email"
  attribute_name          = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress"
  identity_provider_alias = keycloak_saml_identity_provider.realm_azure_saml_identity_provider.alias
  user_attribute          = "email"
}

resource "keycloak_attribute_importer_identity_provider_mapper" "lastname" {
  realm                   = keycloak_realm.master.id
  name                    = "lastname-attribute-importer"
  claim_name              = "lastname"
  attribute_name          = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname"
  identity_provider_alias = keycloak_saml_identity_provider.realm_azure_saml_identity_provider.alias
  user_attribute          = "lastname"
}

resource "keycloak_attribute_importer_identity_provider_mapper" "firstname" {
  realm                   = keycloak_realm.master.id
  name                    = "firstname-attribute-importer"
  claim_name              = "firstname"
  attribute_name          = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname"
  identity_provider_alias = keycloak_saml_identity_provider.realm_azure_saml_identity_provider.alias
  user_attribute          = "firstname"
}

# Group Mapping For UDS Core
resource "keycloak_custom_identity_provider_mapper" "admin" {
  realm                    = keycloak_realm.master.id
  name                     = "admin-group-attribute-importer"
  identity_provider_alias  = keycloak_saml_identity_provider.realm_azure_saml_identity_provider.alias
  identity_provider_mapper = "saml-advanced-group-idp-mapper"

  extra_config = {
    "syncMode"                   = "FORCE"
    "attributes"                 = "[{\"key\":\"http://schemas.microsoft.com/ws/2008/06/identity/claims/groups\",\"value\":\"${var.admin_group_id}\"}]"
    "are.attribute.values.regex" = "false"
    "group"                      = "/${keycloak_group.admin_group.name}"
  }
}

# Disable Required Actions
locals {
  required_actions = [
    {
      realm_id          = keycloak_realm.master.id
      alias             = "CONFIGURE_TOTP",
      name              = "Configure OTP",
      enabled           = false,
      default_action    = false,
      priority          = 10
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "TERMS_AND_CONDITIONS",
      name              = "Terms and Conditions",
      enabled           = false,
      default_action    = false,
      priority          = 20
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "UPDATE_PASSWORD",
      name              = "Update Password",
      enabled           = false,
      default_action    = false,
      priority          = 30
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "UPDATE_PROFILE",
      name              = "Update Profile",
      enabled           = false,
      default_action    = false,
      priority          = 40
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "VERIFY_EMAIL",
      name              = "Verify Email",
      enabled           = false,
      default_action    = false,
      priority          = 50
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "delete_account",
      name              = "Delete Account",
      enabled           = false,
      default_action    = false,
      priority          = 60
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "CONFIGURE_RECOVERY_AUTHN_CODES",
      name              = "Recovery Authentication Codes",
      enabled           = false,
      default_action    = false,
      priority          = 70
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "UPDATE_EMAIL",
      name              = "Update Email",
      enabled           = false,
      default_action    = false,
      priority          = 70
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "webauthn-register",
      name              = "Webauthn Register",
      enabled           = false,
      default_action    = false,
      priority          = 70
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "webauthn-register-passwordless",
      name              = "Webauthn Register Passwordless",
      enabled           = false,
      default_action    = false,
      priority          = 80
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "VERIFY_PROFILE",
      name              = "Verify Profile",
      enabled           = false,
      default_action    = false,
      priority          = 90
    },
    {
      realm_id          = keycloak_realm.master.id
      alias             = "delete_credential",
      name              = "Delete Credential",
      enabled           = false,
      default_action    = false,
      priority          = 100,
    },
    {
      realm_id          = keycloak_realm.master.id
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

# Import the master realm
import {
  to = keycloak_realm.master
  id = "master"
}

# Configure Authenticaion Flows
resource "keycloak_realm" "master" {
  realm   = "master"
  enabled = true

  #browser_flow = "browser-idp-redirect"
}

# Create new authentication flow
resource "keycloak_authentication_flow" "browser_idp_redirect_flow" {
  realm_id    = keycloak_realm.master.id
  alias       = "browser-idp-redirect"
  description = "Browser based authentication with IDP redirect"
  provider_id = "basic-flow"
}

# Create subflow in authentication flow
resource "keycloak_authentication_execution" "idp_redirector" {
  realm_id          = keycloak_realm.master.id
  parent_flow_alias = keycloak_authentication_flow.browser_idp_redirect_flow.alias
  authenticator     = "identity-provider-redirector"
  requirement       = "REQUIRED"
}

# Configure the subflow redirector to point to Azure AD IDP alias
resource "keycloak_authentication_execution_config" "saml_idp_config" {
  realm_id     = keycloak_realm.master.id
  execution_id = keycloak_authentication_execution.idp_redirector.id
  alias        = "Browser IDP"
  config = {
    "defaultProvider" = var.identity_provider_name
  }
}

# Get temp admin user and output user id for import
data "keycloak_user" "admin_user" {
  realm_id = "master"
  username = "admin"
}

import {
  to = keycloak_user.master_admin_user[0]
  id = "master/${data.keycloak_user.admin_user.id}"
}

resource "keycloak_user" "master_admin_user" {
  count         = var.keycloak_admin_user_count
  realm_id      = keycloak_realm.master.id
  username      = var.keycloak_admin_username
  first_name    = ""
  last_name     = ""
  email         = ""
  enabled       = true
}
