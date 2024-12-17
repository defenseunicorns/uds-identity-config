provider "keycloak" {
  client_id     = var.keycloak_admin_client_id
  url           = var.keycloak_admin_url
  username      = var.keycloak_admin_username
  password      = var.keycloak_admin_password
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
