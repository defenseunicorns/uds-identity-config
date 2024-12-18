variable "keycloak_admin_username" {
  description = "Username for the temporary Keycloak Admin user."
}

variable "keycloak_admin_password" {
  description = "Password for the temporary Keycloak Admin user."
}

variable "keycloak_admin_client_id" {
  description = "Keycloak client name that is configured for service accounts."
}

variable "uds_domain" {
  description = "Equivalent to the UDS_DOMAIN variable that was used when UDS Core was deployed."
}

variable "admin_group_id" {
  description = "Object ID for the Administrator Active Directory Group to be mapped to the /UDS Core/Admin Keycloak Group."
}

variable "tenant_id" {
  description = "Full GUID of the tenant of the Entra ID instance you are integrating with Keycloak."
}

variable "identity_provider_name" {
  default = "azure-saml"
}

variable "keycloak_admin_user_count" {
  description = "Control the count of the admin user resource"
  type        = number
  default     = 1
}