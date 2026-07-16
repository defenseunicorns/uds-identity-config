# Findings — callouts for the TDD

Each finding: what we observed, why it matters, and what it implies for the TDD.

---

## F-1 — Variable-substitution syntax: kcc and Keycloak don't share a dialect

**Observed.** TC-1 first-run failed at JSON parse with `IMPORT_VARSUBSTITUTION_ENABLED=true` (kcc default delimiters `$(...)`):

```
Cannot deserialize value of type `java.lang.Integer` from String
"${REALM_ACCESS_TOKEN_LIFESPAN:60}"
```

Tracing kcc internals: it uses Apache commons-text `StringSubstitutor.createInterpolator()`. Two breaking facts about that engine:

1. The colon inside `${...}` is the **lookup-prefix separator** (e.g. `${env:VAR}`, `${file:path}`, `${script:javascript:...}`). It is **not** a default-value separator.
2. The default value-delimiter is `:-` (commons-text default). kcc never calls `setValueDelimiter`, so the engine treats `${REALM_ACCESS_TOKEN_LIFESPAN:60}` as "lookup with prefix `REALM_ACCESS_TOKEN_LIFESPAN`, key `60`" — fails because no such lookup exists.

Keycloak's own realm-import substitution (`org.keycloak.common.util.StringPropertyReplacer`) uses `${VAR}` and `${VAR:default}` (single colon). Confirmed by reading both engines' source.

The two engines do not agree on the colon's meaning. There is **no single placeholder dialect** — with defaults — that both natively accept. This is documented incompatibility upstream: see kcc issue #412, where the maintainer explains kcc v3 used `${...}` defaults and v4 switched to `$(...)` precisely because of this collision.

**Why it matters.** Our `realm.json` mixes two `${...}` flavors:

- 42 unique env-var placeholders (`${UPPER_CASE[:default]}`) intended to be resolved at deploy time from operator-provided Helm values.
- 16 unique Keycloak i18n message refs (`${camelCase}` or `${snake_case}`, e.g. `${role_uma_authorization}`, `${addressScopeConsentText}`) that must stay literal so Keycloak renders translations correctly.

Both share the `${ }` delimiter. Letting kcc loose on the file with native syntax errors immediately on the first env-var placeholder.

**Workaround for the experiment (and likely TDD direction).** Rewrite env-var placeholders to kcc-native lookup syntax during build:

- `${VAR}` → `${env:VAR}`
- `${VAR:default}` → `${env:VAR:-default}`

Leave `${camelCase}` i18n refs untouched.

Then run kcc with:

- `IMPORT_VARSUBSTITUTION_PREFIX='${'`
- `IMPORT_VARSUBSTITUTION_SUFFIX='}'`
- `IMPORT_VARSUBSTITUTION_UNDEFINEDISERROR=false` — so the i18n refs (which have no env var to back them) are passed through untouched instead of erroring.

`scripts/to-kcc-syntax.py` performs this rewrite.

**Authoring implication.** The `realm.json5` source itself can stay in Keycloak's native `${VAR:default}` syntax. The conversion happens at build time. This preserves the `--import-realm` boot path that today's chart uses — important if we keep that path for first-install while letting kcc handle subsequent reconciles.

---

## F-2 — Raw realm exports collide with Keycloak's auto-managed resources

**Observed.** With the kcc-native rewrite in place, kcc got past JSON parse but threw HTTP 403 in `RoleCompositeRepository.addClientComposites`:

```
Error during Keycloak import: HTTP 403 Forbidden
```

Stack trace pointed at composite role mapping for the built-in `realm-management` client. Specifically: kcc was trying to add `manage-realm`, `manage-users`, etc. as composites of `realm-admin` — but the master `admin` user's transitive permissions for the freshly-created realm aren't sufficient at that exact moment, **and** these mappings are auto-created by Keycloak when the realm is created. They're already there.

The realm.json file is a `kcadm get realms/uds` export, which dumps **everything** including:

- The 6 Keycloak built-in clients (`account`, `account-console`, `admin-cli`, `broker`, `realm-management`, `security-admin-console`).
- Their composite role mappings.
- All built-in authentication flows (`builtIn: true`).
- Built-in client scopes (`role_list`, `email`, `profile`, `phone`, etc.).
- Default required actions, default event-type sets, key providers, etc.

Keycloak auto-creates and auto-manages all of these; kcc has no way to "win" against them.

**Why it matters.** The SN's scope preface argues UDS owns only the *essential* subset. F-2 is the empirical proof that the boundary catalog (M1) isn't optional — without it, kcc cannot import a realm at all. The catalog isn't a polish step; it's load-bearing.

**Workaround for the experiment.** `scripts/curate.py` strips the 6 built-in clients, their `roles.client` blocks, and any `scopeMappings`/`clientScopeMappings` that reference them. See "Empirical curation log" below for the running tally.

**Implication for the TDD.** M1 must produce a curated `realm.json` (or a build step that emits one) that contains only UDS-essential resources. The curation is non-trivial — the rest of this document is empirical evidence of the corner cases.

---

## F-3 — kcc 6.x does not URL-encode flow aliases on the sub-flow execution endpoint

**Observed.** With the built-in clients curated out, kcc made it into the auth-flow phase and threw HTTP 404:

```
Cannot create execution 'auth-username-password-form' for non-top-level-flow
'Username/Password Authentication' in realm 'uds-kcc-test': HTTP 404 Not Found
```

The flow alias `Username/Password Authentication` contains a literal `/`. kcc constructs the Admin API URL by concatenating the alias into the path without URL-encoding. The slash terminates the path early, the request misses the intended endpoint, and Keycloak returns 404.

**Why it matters.** Our existing `realm.json5` has a flow named `Username/Password Authentication`. It has worked for years with Keycloak's `--import-realm`. Switching to kcc requires either:

- Renaming the flow to remove the `/` (breaking change for any external reference).
- Patching kcc upstream to URL-encode aliases (small PR, the right long-term fix).

**Workaround for the experiment.** The curator does a global string-replace `Username/Password Authentication` → `Username-Password Authentication` covering the alias and every `flowAlias` reference.

**Implication for the TDD.** Either we rename in `realm.json5` (bigger blast radius — affects existing realms in the field) and ship the rename in a single release, or we contribute the URL-encoding fix upstream and gate on it landing. A small upstream PR is the cleaner answer.

---

## F-4 — Keycloak Admin API refuses to "create" any flow with `builtIn: true`

**Observed.** After the rename in F-3, kcc threw:

```
Cannot create flow 'Authentication Options' in realm 'uds-kcc-test':
Unable to create built-in flows.
```

Reading kcc issue #1251 and Keycloak PR #19794: post-Keycloak 21, the Admin API explicitly refuses to *create* flows whose `builtIn` field is true. Keycloak owns its built-ins; you cannot manage them from outside.

Realm exports include all built-ins regardless. When kcc walks `authenticationFlows[]` and hits one with `builtIn: true` that doesn't yet exist in the new realm, the create call fails.

**Why it matters.** Confirms the curated declared file must contain only `builtIn: false` flows. UDS top-level flows that *reference* built-in sub-flows via `flowAlias` (e.g. `UDS Authentication` referencing the built-in `idp redirector`) continue to work, because the built-in flows exist in the new realm by the time those references are resolved.

**Workaround for the experiment.** `curate.py` drops every `authenticationFlows[]` entry where `builtIn: true`. Both top-level built-ins and built-in sub-flows go.

**Implication for the TDD.** The boundary catalog must mark every authentication flow as either UDS-owned or Keycloak-owned. The build pipeline must strip Keycloak-owned flows before kcc reads the file. UDS-owned customizations of built-in flows (flow extensions, execution overrides) are not currently representable through kcc against modern Keycloak — that's a separate constraint reviewers should know about.

---

## F-5 — Identity provider exports carry fields kcc can't pass through

**Observed.** Round 3 hit HTTP 500 in `IdentityProviderRepository.create`. Two distinct issues in our SAML IdP entry:

1. `firstBrokerLoginFlowAlias: null`. The realm export dumps null for "use Keycloak's default", but the Admin API on create returns 500 instead of defaulting. Filling in `"first broker login"` (the built-in alias) fixed it.
2. `internalId: "1538a301-8427-46d2-b59f-203f2f76e573"`. This is the UUID Keycloak assigned in the original `uds` realm. Our top-level `walk(del(.id))` cleanup didn't strip it. Submitting it to a different realm where the same internalId may conflict (or where Keycloak refuses an external internalId) yields 500.

**Why it matters.** Anything we declare for kcc has to be a *creatable* representation, not a verbatim export. The boundary catalog must include sanitization rules for IdP entries (and likely other resource types) to scrub fields that the Admin API treats as "Keycloak-internal."

**Workaround for the experiment.** `curate.py` (Round 4):

- Replaces null `firstBrokerLoginFlowAlias` with `"first broker login"`.
- Strips `internalId` from every IdP entry.

**Implication for the TDD.** Beyond clients/flows, IdPs and IdP mappers need their own sanitization story. Likely true for `components`, `users` (service accounts), `keys`, etc. as well — to be confirmed once we run drift / reconcile cases. The curation pipeline is a real piece of work, not a one-liner.

---

## F-6 — kcc skips reconcile by default if the input file checksum hasn't changed

**Observed.** TC-5 (drift correction) failed silently. We manually drifted `kcc-test-additive-client.redirectUris` via the Admin API, then re-ran kcc with the same declared file. kcc finished in 721 ms with no operations logged. The drift was **not** corrected.

Inspecting the `uds-kcc-test` realm attributes after the run revealed the cause:

```
de.adorsys.keycloak.config.import-checksum-default: e4b6db…
```

kcc records the SHA256 of the imported file as a realm attribute. On the next run, if the file's checksum matches the stored value, **kcc skips the entire import** — it does not query Keycloak for the live state, doesn't diff, doesn't reconcile drift.

Setting `IMPORT_CACHE_ENABLED=false` disables the optimization. With the flag, the same TC-5 setup ran in 16 s and correctly reverted the drift to the declared value.

**Why it matters.** This is a load-bearing question for the SN, and the answer changes the operational model:

- "Run kcc on every UDS Core upgrade" with the default cache → kcc only does work when the *file* changes. Drift introduced manually between upgrades is invisible to it.
- "Run kcc on every upgrade with `IMPORT_CACHE_ENABLED=false`" → kcc reconciles every time. Slower, but actually idempotent against drift.

**Implication for the TDD.** The reconciler should run with cache disabled. The trade-off is a few extra seconds per upgrade vs. the entire correctness story. The TDD also needs to address whether kcc should run periodically (drift detection cadence — Open Question 5 in the SN) or only on upgrades. With cache disabled, "on upgrade" still doesn't catch drift between upgrades — that's the same gap the SN flagged.

---

## F-7 — kcc applied cleanly against the live `uds` realm Keycloak imported at boot

**Observed.** With `realm-uds-baseline.json` (this directory's curated, kcc-syntax baseline) handed to kcc, against the realm Keycloak created at boot via `--import-realm`:

- **β-soft** (default cache, first kcc run on `uds`): 14.6 s. Snapshot diff returned only:
  - kcc remote-state attributes added (expected — kcc establishing ownership).
  - SAML IdP gained `hideOnLogin: false` (Keycloak default, made explicit).
  - SAML IdP gained `firstBrokerLoginFlowAlias: "first broker login"` (the F-5 fix; was null in the export).
  - Inside `UDS Authentication`, the sub-flow execution previously named `Username/Password Authentication` (slash) was replaced by `Username-Password Authentication` (dash) — matching our declared file. Parent flow execution refs updated atomically; no orphaned flows left.
- **β-hard** (cache disabled, second run): 4.6 s. **Zero-line semantic diff.** True idempotence.
- SSO endpoint healthy throughout. `uds-operator` client + its service-account user untouched. Pepr's auth path intact.

**Why it matters.** This is the production-shape question the SN proposes to solve. The answer is "yes, this works" — `realm-uds-baseline.json` can be dropped onto an existing UDS cluster and kcc reconciles without breakage. A second-run idempotency check confirms there's no churn.

**Implication for the TDD.** The reconciler integration is viable. Build pipeline:

```
src/realm.json5  (authoring source — unchanged today)
  → realm.json  (json5 compile, same as today)
  → realm-uds-kcc.json  (placeholder rewrite, F-1)
  → realm-uds-baseline.json  (curate per F-2..F-5)
```

The chart ships the curated artifact as a ConfigMap, the kcc Job mounts it + `keycloak-realm-env` Secret + UDS_*_DOMAIN env, runs with cache disabled to ensure drift detection on every reconcile. Open Question 5 in the SN (drift detection cadence) collapses to "every reconcile, free."

---

## F-8 — kcc remote-state can be cleared in-place to "re-adopt" without realm rebuild

**Observed.** When iterating on the curated baseline (e.g. moving from R4 to R5), the live `uds` realm carried kcc's prior remote-state attributes (`de.adorsys.keycloak.config.state-default-roles-realm-0` and similar). Applying a more aggressively-curated declared file with that state still in place would cause kcc to interpret "I previously owned X but X is no longer declared" as **delete X** (with default `IMPORT_MANAGED_*=full` modes), risking destruction of resources Keycloak auto-manages (`uma_authorization`, `default-roles-uds`, etc.).

Workaround: clear all `de.adorsys.keycloak.config.*` attributes from the realm before applying the new baseline:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/admin/realms/uds" \
  | jq '.attributes |= with_entries(select(.key | startswith("de.adorsys.keycloak.config") | not))' \
  | curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d @- "$BASE/admin/realms/uds"
```

Verified non-destructive: the actual resources (clients, scopes, roles, IdPs) stay in place. Only kcc's ownership memory is wiped. Next kcc run re-adopts everything in the new declared file as kcc-owned, ignores everything else.

**Why it matters.** Iterating on the boundary catalog after first adoption is a real workflow — curators will narrow / widen scope as the catalog matures. Without F-8, every catalog change risks data loss. With it, catalog evolution is safe.

**Implication for the TDD.** Operationally, the reconciler bundle should expose a "reset ownership" command (or a reconciler-level option). Alternative: ship default `IMPORT_MANAGED_*=no-delete` so that even with stale ownership state, kcc never deletes; the no-delete posture removes the urgency of clearing state.

---

## F-9 — UUID-bearing fields must all be stripped, not just `id`

**Observed.** After applying the upstream `walk(del(.id))` step in the build pipeline, an audit (regex for UUID-shaped strings in the curated baseline) found one UUID still present:

```
groups[*].subGroups[*].parentId = 53521ed0-aa44-4007-9727-0015d5281c3e
```

`parentId` references the parent group's UUID. After we strip the parent's `id`, this reference is dangling — points to a UUID nothing on a fresh cluster will own. Empirically Keycloak/kcc fall back to the `path` field for parent linkage so this didn't break β-soft, but it's a latent failure mode: if a future Keycloak version starts validating UUID references, applying our baseline to a fresh cluster would error.

A full audit of all reference-shaped fields confirmed every other reference uses **stable name-based identifiers**:

- `clientId: "uds-operator"` (client name)
- `browserFlow: "UDS Authentication"` (flow alias)
- `firstBrokerLoginFlowAlias: "first broker login"` (flow alias)
- `directGrantFlow`, `resetCredentialsFlow`, `clientAuthenticationFlow`, etc. — all flow aliases
- SAML `entityId`, `idpEntityId`, `webAuthnPolicy*RpId` — URLs / hostnames, not UUIDs

Only `parentId` was the offender.

**Why it matters.** Cross-cluster portability of the baseline depends on having zero hardcoded Keycloak-internal IDs. Every UUID we leave in the file is a future failure mode.

**Fix.** Curator (`curate.py`) now strips `parentId` recursively after the upstream `walk(del(.id))` step. Audit confirmed no UUID-shaped strings remain in the curated baseline.

**Implication for the TDD.** The build pipeline rule is "strip every UUID-bearing field, not just `id`." The complete strip set today: `id`, `internalId` (F-5), `parentId`. CI should regex-audit the curated output for any remaining UUID-shaped strings on every PR, so a future Keycloak version that introduces a new UUID-bearing field gets caught immediately.

---

## F-10 — Sub-flow execution priorities silently reset to 0 (upstream kcc bug)

**Observed.** When kcc creates a sub-flow's executions, it does not pass the declared `priority` value to Keycloak's `addExecutionToFlow` Admin API. Keycloak then auto-assigns a priority (starting at 0, incrementing per execution), ignoring whatever the source declared.

**Confirmed by minimal repro:**

```json
{
  "alias": "Conditional 2FA",
  "topLevel": false,
  "authenticationExecutions": [
    {
      "authenticator": "conditional-user-configured",
      "requirement": "REQUIRED",
      "priority": 1
    }
  ]
}
```

After `kcc` runs against a fresh Keycloak 26.5.7:

```
Conditional 2FA / conditional-user-configured → priority 0   (declared 1)
```

Top-level flow executions are not affected — kcc preserves their priorities correctly. Only sub-flow executions hit this.

**Root cause** (read from kcc source `ExecutionFlowsImportService.createExecutionForSubFlow` + `ExecutionFlowRepository.createSubFlowExecution`): kcc builds a `HashMap<String,String>` for the sub-flow create payload containing `alias`, `provider`, `type`, `description`, `authenticator` — but NOT `priority`. Keycloak 26.5.7's `addExecutionToFlow` falls into the `getNextPriority(parentFlow)` branch and auto-assigns 0 (or next-incremented).

The follow-up `configureExecutionFlow` PUT/update path tries to set the priority but the Admin API's `update` endpoint does not honor priority changes for sub-flow executions (related to Keycloak issue #20747).

**Functional impact in our realm.** Categorized by execution-type and Keycloak's runtime semantics:

| Sub-flow | Executions | Type | Impact |
|---|---|---|---|
| `Authentication` | cookie / idp / x509 / username-password | ALTERNATIVE | None practical. Alternatives run in priority order until one succeeds; cookie wins for logged-in users; idp/x509 are conditional (no-op when not configured); username-password is fallback. Priority compaction doesn't change which one wins. |
| `Conditional OTP` | conditional-user-configured / auth-otp-form / webauthn-authenticator | REQUIRED | None practical. REQUIRED executions run sequentially. With all priorities collapsed to 0, Keycloak processes them in insertion order — which matches source order. |
| `UDS registration form` and other custom sub-flows | various | REQUIRED | Same — insertion order matches source intent. |

**Verdict.** The bug is real and it's driving the test diff (`priority: 1` → `priority: 0`), but for our specific realm structure it doesn't change runtime behavior because:

1. ALTERNATIVE executions in our flows have mutually-exclusive conditions, so order is dominated by which condition succeeds, not by priority numbers.
2. REQUIRED executions are listed in source in the order we want them to run, and Keycloak processes same-priority executions in insertion order.

**Implication for the TDD.** Cosmetic, not blocking. Worth noting as a known divergence between `--import-realm` (preserves declared priority) and kcc (collapses sub-flow priorities to 0). If we ever introduce a sub-flow where two REQUIRED executions need a specific order that doesn't match source array order, OR an ALTERNATIVE flow where same-priority means parallel/race, the bug could surface as a real behavior change. Prevention: keep authoring sub-flow executions in the order they should run.

**Filed upstream** as [adorsys/keycloak-config-cli#1539](https://github.com/adorsys/keycloak-config-cli/issues/1539) with minimal repro and suggested one-line fix (include `priority` in the create payload).

---

## Empirical curation log (round-by-round)

Each round is "kcc ran further than before; here is what stopped it."

| Round | Removal / change | Justification |
|-------|------------------|---------------|
| R1 | 6 Keycloak built-in clients (`account`, `account-console`, `admin-cli`, `broker`, `realm-management`, `security-admin-console`) and their `roles.client` blocks. | F-2 — Keycloak auto-manages these; declarations collide. |
| R1 | `scopeMappings` / `clientScopeMappings` entries referencing those clients. | F-2 — dangling references. |
| R2 | Rename flow `Username/Password Authentication` → `Username-Password Authentication` (alias + all `flowAlias` refs). | F-3 — kcc URL-encoding bug on `/`. |
| R3 | All `authenticationFlows[]` entries where `builtIn: true`. | F-4 — Keycloak Admin API refuses to create them. |
| R4 | IdP `firstBrokerLoginFlowAlias: null` → `"first broker login"`. | F-5 — Admin API doesn't default null. |
| R4 | IdP `internalId` field stripped. | F-5 — Keycloak-internal field; collisions on cross-realm reuse. |
| R5 | 11 Keycloak built-in client scopes (`acr`, `address`, `email`, `microprofile-jwt`, `offline_access`, `openid`, `phone`, `profile`, `role_list`, `roles`, `web-origins`). | Auto-created by Keycloak per realm; declaring is redundant noise. |
| R5 | Default realm roles (`uma_authorization`, `offline_access`, `default-roles-<realm>`). | Auto-created. |
| R5 | Service-account users (`service-account-*`). | Auto-created with their parent client when `serviceAccountsEnabled: true`. |
| R5 | `components` (KeyProvider, WorkflowProvider, ClientRegistrationPolicy, UserProfileProvider). | Auto-created per realm. |
| R5 | Top-level `requiredActions`, `defaultRole`, `defaultDefaultClientScopes`, `defaultOptionalClientScopes`. | Auto-managed. |
| R5 | Empty `smtpServer: {}`, `localizationTexts: {}`, `supportedLocales: []`. | Default empty containers. |

After R5 (the maximally-stripped baseline saved as `realm-uds-baseline.json` — 40 KB / 1234 lines, 43% smaller than R4's output), kcc runs end-to-end on a fresh import in **5 seconds**. Adopts the live `uds` realm Keycloak imported at boot in **3.7 seconds** with zero semantic changes after kcc state is cleared (F-8). Idempotent re-run in **3.6 seconds** with zero diff.

After R4, kcc runs end-to-end on a fresh import in **8 seconds**, producing:

- All 8 clients (6 Keycloak built-ins auto-created + 2 UDS custom).
- 12 authentication flows (Keycloak built-ins + 5 UDS top-level customs + their sub-flows).
- 26 client scopes.
- The Google SAML IdP with `wantAssertionsSigned=true` preserved.
- All 16 i18n message refs preserved literal (e.g. `${role_uma_authorization}`).

This is the working baseline for subsequent test cases (TC-2 onward).

---
