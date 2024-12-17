variable "keycloak_admin_client_id" {
  description = "Client ID for the Keycloak admin"
  type        = string
}

variable "keycloak_admin_username" {
  description = "Username for the Keycloak admin"
  type        = string
}

variable "keycloak_admin_password" {
  description = "Password for the Keycloak admin"
  type        = string
  sensitive   = true
}

variable "keycloak_admin_url" {
  description = "URL for accessing the Keycloak admin panel"
  type        = string
}

variable "keycloak_idp_alias" {
  description = "Alias for the Keycloak IDP"
  type        = string
}

variable "admin_user_count" {
  description = "Control the count of the admin user resource"
  type        = number
  default     = 1
}

provider "keycloak" {
  client_id     = var.keycloak_admin_client_id
  url           = var.keycloak_admin_url
  username      = var.keycloak_admin_username
  password      = var.keycloak_admin_password
}

resource "keycloak_user" "master_admin_user" {
  count         = var.admin_user_count
  realm_id      = "master"
  username      = "admin"
  first_name    = ""
  last_name     = ""
  email         = ""
  enabled       = true
}