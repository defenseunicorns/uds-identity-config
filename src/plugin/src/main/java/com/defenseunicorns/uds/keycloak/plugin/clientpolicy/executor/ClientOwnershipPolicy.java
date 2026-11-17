/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */
package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.keycloak.json.RawJsonValue;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.clientpolicy.ClientPolicyException;

import java.util.HashSet;

/**
 * Reads HTTP-filter enrollment from UDS executor profiles in the current realm.
 * Enrollment is the union of configured client lists in profiles attached to enabled policies.
 * Missing executor configuration uses the default UDS callers; an explicit empty list enrolls none.
 */
public final class ClientOwnershipPolicy {
    private ClientOwnershipPolicy() {}

    /**
     * Checks whether an enabled client policy references a UDS executor profile enrolling the caller.
     * Detached profiles, profiles without executors, and executors from other providers are ignored.
     *
     * @param session session supplying the current realm and its client profiles
     * @param clientId authenticated caller's client ID; null or blank IDs are not enrolled
     * @return true if an active UDS executor configuration enrolls the caller, otherwise false
     * @throws IllegalStateException if policies or profiles cannot be read, so configuration failures do not bypass ownership
     */
    public static boolean isManagedClient(KeycloakSession session, String clientId) {
        try {
            var realm = session.getContext().getRealm();
            var policies = session.clientPolicy().getClientPolicies(realm, false);
            if (policies.getPolicies() == null) return false;
            var activeProfiles = new HashSet<String>();
            for (var policy : policies.getPolicies()) {
                if (Boolean.TRUE.equals(policy.isEnabled()) && policy.getProfiles() != null) {
                    activeProfiles.addAll(policy.getProfiles());
                }
            }
            if (activeProfiles.isEmpty()) return false;
            var profiles = session.clientPolicy().getClientProfiles(realm, false);
            if (profiles.getProfiles() == null) return false;
            for (var profile : profiles.getProfiles()) {
                if (!activeProfiles.contains(profile.getName()) || profile.getExecutors() == null) continue;
                for (var executor : profile.getExecutors()) {
                    if (!UDSClientPolicyPermissionsExecutorFactory.PROVIDER_ID.equals(executor.getExecutorProviderId())) continue;
                    var configuration = RawJsonValue.unwrap(UDSClientPolicyPermissionsExecutorConfiguration.class,
                            executor.getConfiguration());
                    if (configuration == null) configuration = new UDSClientPolicyPermissionsExecutorConfiguration();
                    if (configuration.enforcesOwnershipFor(clientId)) return true;
                }
            }
            return false;
        } catch (ClientPolicyException e) {
            throw new IllegalStateException("Could not read ownership enforcement configuration", e);
        }
    }
}
