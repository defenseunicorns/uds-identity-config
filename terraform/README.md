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
        terraform import -var-file="admin-creds.tfvars" keycloak_realm.master master
        terraform plan -var-file="./admin-creds.tfvars"
        terraform apply -var-file="./admin-creds.tfvars"
      ```

* **These first steps will output the admin user id to use in the next set of steps**

1. Remove admin user
    * ```bash
        cd uds-identity-config/terraform/remove-admin
        terraform init
        terraform import -var-file="../admin-creds.tfvars" keycloak_user.master_admin_user[0] master/<admin user id>
        terraform plan -var="admin_user_count=0" -var-file="../admin-creds.tfvars"
        terraform apply -var="admin_user_count=0" -var-file="../admin-creds.tfvars"
      ```
