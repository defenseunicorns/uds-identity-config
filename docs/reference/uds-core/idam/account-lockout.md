---
title: Account Lockout
---

## Lockout Behavior Options

UDS Core exposes one configurable option related to brute‑force protection:

### **Max Temporary Lockouts**

This controls whether your realm uses:

1. Permanent lockout only: `MAX_TEMPORARY_LOCKOUTS = 0` (default)
1. Temporary lockout followed by permanent lockout: `MAX_TEMPORARY_LOCKOUTS > 0`

Use the following bundle override to configure this functionality:
```yaml
overrides:
  keycloak:
    keycloak:
      values:
        - path: realmInitEnv
          value:
            MAX_TEMPORARY_LOCKOUTS: 3
```

### Example flows

#### 1. Default UDS Core behavior: **permanent lockout** (`MAX_TEMPORARY_LOCKOUTS = 0`)

1. User fails login **3 times** within a 15 minute window.
1. Keycloak applies a **permanent lockout** once the threshold is hit.
1. The account remains locked until an administrator manually unlocks it.

#### 1. Optional: temporary then permanent mode (`MAX_TEMPORARY_LOCKOUTS > 0`)

1. Configure `MAX_TEMPORARY_LOCKOUTS` to a non‑zero value.
1. User fails login **3 times within the 15 minute window** → Keycloak applies a **temporary lockout** for 15 minutes.
1. After the temporary lockout expires, the account unlocks.
1. If the user triggers more temporary lockouts than allowed by the configured `MAX_TEMPORARY_LOCKOUTS` value, Keycloak escalates the account to a **permanent lockout**. The user has **up to 12 hours** during which additional lockouts count toward this limit. For example:

   * `MAX_TEMPORARY_LOCKOUTS = 1` → the **second** lockout results in permanent lock
   * `MAX_TEMPORARY_LOCKOUTS = 2` → the **third** lockout results in permanent lock

## Manually Configure Temporary Lockouts

### Admin Console (Keycloak 24+)

1. Sign into the Keycloak Admin Console and select your realm.

2. Navigate to **Realm Settings → Security Defenses → Brute Force Detection**.

3. Configure the following values:

   * Brute Force Protected: `Lockout permanently after temporary lockout`
     Enables Keycloak’s brute-force detection mechanism for the realm.

   * Failure Factor: `3`
     The number of failed login attempts within the counting window that triggers a lockout.

   * Quick Login Check (ms): `1000`
     If repeated failed attempts occur faster than this interval, Keycloak treats them as rapid-attack behavior and applies the minimum quick-login wait.

   * Max Delta Time (s): `43200`
     A rolling 12-hour window during which failed login attempts count toward the failure threshold.

   * Wait Increment (s): `900`
     The duration of a temporary lockout (15 minutes) after the failure threshold is reached.

   * Minimum Quick Login Wait (s): `60`
     The minimum delay applied when rapid successive failures occur.

   * Max Failure Wait (s): `86400`
     The maximum temporary lockout wait (24 hours). This sets the upper bound for how long Keycloak can delay a user before escalation.

   * Failure Reset Time (s): `43200`
     Controls when Keycloak resets the failure and lockout counters. Must exceed the rolling window to allow temporary lockouts to accumulate toward permanent lockout.

   * Permanent Lockout: `ON`
     Enables escalation to a permanent lockout once the configured number of temporary lockouts is exceeded.

   * Max Temporary Lockouts: `3`
     Allows one temporary lockout before escalating to a permanent lock.

   * Brute Force Strategy: `MULTIPLE`
     Defines how Keycloak handles the progression of lockout waits when repeated failures occur.

4. Save and test with a non-production account.

## Compliance
:::caution
There may be compliance impacts when modifying lockout behavior, be aware of any environment specific requirements (NIST controls or STIG requirements) for brute-force protection.
:::
