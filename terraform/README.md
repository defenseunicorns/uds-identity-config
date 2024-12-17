# Configure Master Keycloak Realm

## What's Happening:
1. Required actions are turned off
    * This disables the default admin user from updating their password, also aids in disabling username/password in the master realm
2. New Authentication flows is created and configured to be default browser flow
    * this results in no username password form being presented, instead a user is redirected to the IDP immediately.
        * if IDP is misconfigured or not setup, keycloak will fail and present a screen that says `Invalid Username / Password`
3. New Terraform Client is created
    * service account client to be able to run terraform in the future
    * configured with proper service account roles to manage the master realm
4. Configure Azure IDP for allowing new admin users to be setup
    * mapper configured to only allow Azure AD group < azure group here > to be created as admins
5. Remove the default temporary Keycloak admin user

## Requirements
* Terraform
* Access to Keycloak admin portal
* An Azure AD IDP is created and correctly configured

## Steps
1. Update `admin-creds.tfvars` with specific environment variables
2.  Configure Master Realm
    * ```bash
        cd uds-identity-config/terraform
        terraform init
        terraform plan -var-file="./admin-creds.tfvars"
        terraform apply -var-file="./admin-creds.tfvars"
      ```
3. When compeletely sure that admin user can be registered via Azure AD IDP and/or the terraform client credentials work with terraform re-run these:
    * ```bash
        terraform plan -var="keycloak_admin_user_count=0" -var-file="./admin-creds.tfvars"
        terraform apply -var="keycloak_admin_user_count=0" -var-file="./admin-creds.tfvars"
      ```

    * To configure a keycloak provider with the terraform client credentials:
        * ```bash
            provider "keycloak" {
                client_id     = < keycloak terraform client id, default (terraform-client)>
                url           = var.keycloak_admin_url
                client_secret = < client-secret >
            }
          ```
        * The client secret can be retrieved in the Keycloak Admin Portal:
            1. Admin Portal
            2. Select Master Realm - Top Left Drop down
            3. Select Clients - Left Menu Tab
            4. Select terraform-client
            5. Select Credentials - Top Tab
            6. Copy client secret
        * Alternatively the client secret was generated and should be accessible via terraform state
