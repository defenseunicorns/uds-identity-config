provider "keycloak" {
  client_id     = var.keycloak_admin_client_id
  url           = var.keycloak_admin_url
  username      = var.keycloak_admin_username
  password      = var.keycloak_admin_password
}

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