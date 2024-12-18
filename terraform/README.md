# Configure Master Keycloak Realm

## What's Happening:
1. Create Keycloak Terraform Client
    1. Terraform Client allows service accounts
        * Important for running terraform without user credentials for Day 1 & 2 ops
    2. Generate Client Secret
        * Should be saved or retrieved from Keycloak Admin UI
    3. Assign Master Realm Admin Role to Service Account Roles
        * This provides the service account the needed permissions to perform realm management
2. Create Master Realm Admin User Group
    1. Map group to the Realm Admin Role
        * This group is called `admin-group` which has the Realm `admin` role, which gives the user complete relam management control
3. Create Azure AD IDP
    1. Setup user attributes to be mapped into Keycloak users from Azure AD
    2. Setup mapper for mapping Azure AD group to Keycloak admin group
4. Disable Username Password
    1. Disable required actions for admin users
        * Security measure so that admin users can't create passwords and get into undesired states
    2. Configure new authentication flow to automatically redirect to Azure IDP
        * No Keycloak landing page will be reached, just direct to Azure
5. Manage the Keycloak tmp Admin User
    1. When the variable `keycloak_admin_user_count=0` is provided to the terraform apply the tmp admin user is deleted
        * **Very Important to not delete this user until satisfied that registering new admin users via the IDP works and the terraform client successfully works for running terraform applies**

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
