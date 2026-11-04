# Migrating client ownership

This guide covers the ownership protection introduced by PR #963. It applies to
service-account applications managing clients in the `uds` realm, including client
roles accessed through either `/clients/{client-uuid}/roles/...` or
`/roles-by-id/{role-uuid}`.

This is a breaking change for **non-admin service accounts that manage existing
clients without the correct ownership attribute**. Full administrators, including
the bundled OpenTofu account with `realm-admin`, do not need an ownership migration.

## How ownership works

A managed client stores the owning application's **client ID** in its `created-by`
attribute. This is the application's readable `clientId`, not its internal UUID,
service-account username, or Kubernetes service-account name.

The authenticated application must match that value. The application must also have
the native Keycloak permissions needed for the operation. Ownership does not grant
`manage-clients` or other administrative permissions.

A client role inherits its client's ownership. Do not add `created-by` attributes to
individual roles. Changing the client owner changes which non-admin application can
manage all of that client's roles. Realm roles have no containing client and remain
subject to native Keycloak authorization.

Non-admin service accounts are subject to ownership checks even when their client
IDs are not `uds-operator` or `uds-fleet-admin`. The existing naming rules remain:
clients managed by `uds-fleet-admin` require the `fleet-` prefix; `uds-operator` and
other service-account applications have no ownership-specific prefix requirement.

Full administrators bypass ownership checks, including role ownership. Their client
create/update requests still pass through the enabled Client Policy. For example,
an admin update that submits a disallowed mapper or scope can still be rejected.
Administrators can change or disable that policy. The bundled OpenTofu account is
exempt because of its administrative role, not because of its name.

## What will and will not work

The table assumes the ownership filter and UDS Client Policy are enabled and that
the caller has the necessary native Keycloak permission.

| Caller and operation | Result after upgrade | Migration required? |
| --- | --- | --- |
| Non-admin application creates a new client | The policy stamps `created-by` with the authenticated application's client ID. A supplied value for another owner is overwritten. Naming and configured mapper/scope rules still apply. | No migration for newly created clients. |
| Non-admin application reads, updates, or deletes a correctly stamped client | Allowed by ownership. Updates cannot transfer ownership to another application. | No. |
| Non-admin application reads, updates, or deletes an unstamped client | Rejected by the Client Policy with HTTP 400 and `unauthorized_client`. An ordinary update cannot claim the client because ownership is checked first. | A trusted administrator must assign the correct owner. |
| Non-admin application accesses a client stamped for a different application | Direct client reads and writes are rejected. | Do not reassign it unless the administrator has confirmed a deliberate transfer. |
| Non-admin application lists clients | Listing remains available under native permissions. Seeing a client in a list does not imply permission to read or manage it directly. | No; do not use list visibility as an ownership test. |
| Owner manages its client's roles by name or UUID | Allowed by ownership, including role reads, updates, deletion, and role subresources. | No separate role migration. |
| Non-owner manages another client's roles by name or UUID | Rejected with HTTP 403. Knowing the role UUID does not bypass ownership. | Migrate the containing client only if this application is its verified owner. |
| Full administrator manages an unowned or differently owned client or its roles | Ownership permits it. Native permissions and applicable enabled policy checks still apply. Admin updates do not automatically change `created-by`. | No ownership migration, including for full-admin OpenTofu. |
| Automation uses an account that no longer has full admin privileges | It becomes subject to ownership if it is a service account. Previously admin-created clients may be unstamped. | Inventory and migrate the clients it should retain before removing the role. |
| Ordinary human caller uses a non-managed application | Native Keycloak authorization continues to apply. This change does not grant additional access. | No service-account ownership migration. |

Missing resources can still return native Keycloak errors. Use an existing client
and role when testing an ownership denial.

## Before upgrading

1. **Identify affected automation.** Inventory service-account applications that
   create, reconcile, read, update, or delete clients or client roles. Include
   custom integrations with `manage-clients`, not only the bundled applications.
2. **Record the actual administrative roles.** Separate full administrators from
   limited service accounts. Do not assume that a name containing `admin` means
   the account has `realm-admin`. Do not grant full administration just to avoid
   migration.
3. **Map applications to their managed clients.** Use deployment manifests,
   infrastructure state, and operator records. Record the owning application's
   `clientId`, each target client's UUID and `clientId`, and its existing
   `created-by` value. Never infer ownership solely from a caller's request or
   from the fact that a client has no owner.
4. **Resolve shared management.** `created-by` holds one owner, not a list. If two
   limited applications previously managed the same client, choose one owning
   application and change the workflow before upgrading. Comma-separated owners
   are not supported. Full administrators retain their intentional exemption.
5. **Pause affected reconciliation and client changes.** Prevent concurrent
   updates while auditing and applying ownership. Keep a working full-admin
   session available for migration and recovery.
6. **Back up the deployment using your normal recovery procedure.** Also save the
   per-client ownership inventory. Keep migration records in a restricted
   directory. The commands below save ownership state, not a complete deployment
   backup.
7. **Check policy compatibility separately.** Review mapper and scope settings
   submitted by automation, including full-admin tools. A full client
   representation can include existing settings that the enabled policy rejects.
   Adjust the settings or deliberately configure the policy before resuming that
   workflow. Ownership migration does not change mapper or scope configuration.

Prefer audited backfilling before activating the new plugin. If it is already
active, a full administrator can perform the same migration; affected non-admin
automation remains blocked until its clients are correctly stamped.

## Inventory clients

The examples use `curl`, `jq`, and an already obtained full-admin access token.
Use your normal administrative authentication process. Set the URL to the admin
origin for your deployment, without a trailing slash. Do not disable TLS
verification; configure your trusted CA when needed.

```sh
set -euo pipefail
export KEYCLOAK_ADMIN_URL='https://<keycloak-admin-host>'
export ADMIN_TOKEN='<full-admin-access-token>'
umask 077
mkdir -p ownership-migration

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KEYCLOAK_ADMIN_URL/admin/realms/uds/clients" \
  | jq '[.[] | {id, clientId, owner: (.attributes["created-by"] // null)}]' \
  > ownership-migration/inventory.json
```

Inspect the response and complete your application-to-client mapping before making
changes. If your deployment or tooling paginates the list, retrieve every page.
An existing correct ownership stamp should remain unchanged. An unexpected owner
requires investigation, not automatic replacement.

## Assign one verified owner

Repeat this procedure only for clients from the approved inventory. Replace the
placeholders with the audited application client ID and target client UUID.

```sh
export OWNER_CLIENT_ID='<owning-application-clientId>'
export TARGET_CLIENT_UUID='<target-client-UUID>'
export TARGET_URL="$KEYCLOAK_ADMIN_URL/admin/realms/uds/clients/$TARGET_CLIENT_UUID"

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$TARGET_URL" \
  | jq '{id, clientId,
         ownerPresent: ((.attributes // {}) | has("created-by")),
         owner: (.attributes["created-by"] // null)}' \
  > "ownership-migration/$TARGET_CLIENT_UUID.before.json"

cat "ownership-migration/$TARGET_CLIENT_UUID.before.json"
```

Confirm the target ID, client name, and previous owner against the inventory. Stop
if they differ. Confirm that `OWNER_CLIENT_ID` is the application that will make the
requests. For Fleet, ensure the target client name satisfies its `fleet-` rule.
Renaming clients can affect callers; plan any required rename separately.

For the Keycloak 26.8.1 version used by this change, the Admin API accepts an
attributes-only update and preserves unrelated client settings. Submit only the
ownership change:

```sh
jq -n --arg owner "$OWNER_CLIENT_ID" \
  '{attributes: {"created-by": $owner}}' \
  > "ownership-migration/$TARGET_CLIENT_UUID.update.json"

curl --fail-with-body --silent --show-error \
  -X PUT --write-out 'HTTP %{http_code}\n' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary "@ownership-migration/$TARGET_CLIENT_UUID.update.json" \
  "$TARGET_URL"

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$TARGET_URL" \
  | jq -e --arg owner "$OWNER_CLIENT_ID" \
      '.attributes["created-by"] == $owner'
```

Expect HTTP 204 from the update and `true` from the final check. Stop on any failed
request. Do not copy the entire client list into update requests or bulk-assign
all unstamped clients. Built-in clients and clients belonging to other applications
must not be claimed by the migrating integration.

If an account's client ID is renamed later, audit the clients stamped with the old
name. Ownership uses that name and does not automatically follow a rename. Treat
client ID reuse as an ownership reassignment decision and inspect existing stamps.

## Verify with the actual non-admin application

Obtain an access token using the application's normal authentication flow. A
successful admin request is not proof that the application's migration succeeded.
Set `APPLICATION_TOKEN` to that token and select an existing client role of the
migrated client. Use the role's internal UUID for `ROLE_UUID`.

```sh
export APPLICATION_TOKEN='<application-access-token>'
export ROLE_UUID='<role-UUID-belonging-to-the-migrated-client>'

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $APPLICATION_TOKEN" "$TARGET_URL" \
  | jq '{id, clientId, owner: .attributes["created-by"]}'

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $APPLICATION_TOKEN" \
  "$KEYCLOAK_ADMIN_URL/admin/realms/uds/roles-by-id/$ROLE_UUID" \
  | jq '{id, name, clientRole, containerId}'
```

Both reads should succeed. Then:

1. Run one normal reconciliation or planned, reversible update of the migrated
   client and confirm `created-by` still matches the application.
2. Use a disposable client-role fixture to verify role creation, read, update, and
   deletion through the application's normal route. Verify the UUID route too if
   the integration uses it. Do not test deletion against a production role.
3. With a different non-admin application's token, attempt to read the same role
   by UUID and by its client-role name route. Expect HTTP 403. A full-admin token
   is not suitable for this denial check because its exemption is intentional.
4. Verify that a full administrator can still access that role and can perform
   policy-compliant client updates without changing ownership.
5. Resume one integration first. Check its reconciliation status and errors before
   resuming the remaining integrations. Keep the approved mapping and results.

## Troubleshooting

| Symptom | Check and action |
| --- | --- |
| HTTP 400 with `unauthorized_client` on a client operation | Compare the authenticated application's `clientId` with the stored `created-by`. Use a full administrator to correct an audited mismatch. Do not retry the same update to try to acquire ownership. |
| HTTP 403 on `/roles-by-id/...` or a client's role route | Check the containing client's owner and the caller's native permissions. Do not stamp the role itself. |
| Listing works but a direct client read fails | Listing does not establish ownership. Check the target's stamp. |
| HTTP 400 with `invalid_client` | Read the policy error. Check Fleet naming requirements and submitted mapper/scope settings. Ownership changes alone do not resolve a settings violation. |
| A full-admin operation fails | Confirm the account's actual administrative roles, target realm, native permissions, and policy-compliance of the submitted settings. The exemption is not a bypass of native authorization or settings validation. |
| Automation worked before its admin role was removed | Inventory the clients it managed while exempt; admin writes do not automatically stamp them. Backfill only its verified targets. |

## Rollback and recovery

Keep affected automation paused while correcting a migration mistake. Use a full
administrator, compare against the saved `*.before.json` record, and change only
the affected ownership assignment.

- If a previous owner existed, restore that exact value with the same
  attributes-only update procedure. Confirm it with a GET and re-test the intended
  caller.
- If the client previously had no owner, setting `created-by` to an empty string
  revokes the newly assigned non-admin ownership. It does not recreate an absent
  attribute byte for byte. Use your deployment backup and recovery procedure if
  exact prior metadata must be restored. Do not assume that omitting an attribute
  or sending `null` removes it in Keycloak 26.8.1.
- Returning a client to an unowned state while this protection is active will
  block non-admin management again. Keep admin access available and correct the
  migration before resuming automation.
- A deployment rollback should follow your normal version and data recovery
  procedure. Do not automatically roll back a whole realm to correct one stamp.

`UDS_CLIENT_OWNERSHIP_FILTER_ENABLED=false` is **not an ownership migration or a
complete rollback**. It disables the request/response filters, including the role
ownership checks, after the Keycloak process picks up the environment change. It
does not disable Client Policy ownership checks on client CRUD. The value `false` is case-insensitive. Other values, including an unset variable,
leave the filter enabled.

Disabling the UDS Client Policy is a separate administrative action. It stops this
executor's CRUD ownership enforcement and automatic stamping, while the enabled
filter still checks role access. Clients created by non-admin applications during
that interval may need an audited ownership backfill before the policy is enabled
again. Prefer correcting ownership over disabling either control.

## Remaining boundary

This migration does not resolve the pre-existing mapper/scope subresource
allowlist gap documented in the security review. Client create/update
representations are validated, but dedicated mapper/scope APIs do not uniformly
apply those allowlists. Role ownership protection must not be treated as proof
that every client-setting write route has equivalent policy enforcement.
