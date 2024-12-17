variable "keycloak_admin_username" {
  description = "Username for the temporary Keycloak Admin user."
}

variable "keycloak_admin_password" {
  description = "Password for the temporary Keycloak Admin user."
}

variable "keycloak_admin_url" {
  description = "Url for accessing the admin Keycloak Portal."
}

variable "keycloak_admin_client_id" {
  description = "Keycloak client name that is configured for service accounts."
}

variable "keycloak_idp_alias" {
  description = "Alias for the Keycloak IDP that should be redirected to."
}

variable "keycloak_admin_user_count" {
  description = "Control the count of the admin user resource"
  type        = number
  default     = 1
}