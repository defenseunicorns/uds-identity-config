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