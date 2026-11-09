/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import com.fasterxml.jackson.databind.JsonNode;
import org.keycloak.json.RawJsonValue;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.ClientPolicyExecutorRepresentation;
import org.keycloak.representations.idm.ClientProfileRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutorFactory.PROVIDER_ID;

/**
 * Synchronizes native mapper delegation with the union of configured UDS client profiles.
 * Only types added by this helper are removed when configuration changes. Preexisting realm
 * allowances remain independent, including types that also appear in a UDS profile.
 * Re-adding an already-present UDS-managed type manually cannot transfer its ownership.
 */
public final class ProtocolMapperDelegationSyncer {
    /**
     * Keycloak 26.8.2's {@code Constants.ALLOWED_PROTOCOL_MAPPER_TYPES_FOR_CLIENT_ADMINISTRATORS},
     * read by {@code ProtocolMappersResource.requireManageProtocolMappers} as comma-separated IDs.
     * Use the literal because the constant is absent in 26.8.1, our preparatory upgrade target.
     * @see <a href="https://github.com/keycloak/keycloak/blob/26.8.2/server-spi-private/src/main/java/org/keycloak/models/Constants.java#L276">Native attribute</a>
     * @see <a href="https://github.com/keycloak/keycloak/blob/26.8.2/services/src/main/java/org/keycloak/services/resources/admin/ProtocolMappersResource.java#L329">Native permission check</a>
     */
    public static final String ALLOWED_MAPPERS_ATTRIBUTE = "allowedProtocolMapperTypesForClientAdministrators";
    static final String UDS_ADDED_MAPPERS_ATTRIBUTE = "uds.protocolMapperDelegation.addedTypes";

    /** Prevents construction of this stateless synchronizer. */
    private ProtocolMapperDelegationSyncer() {}

    /**
     * Applies the new persisted profiles in the caller's transaction, without reading policy caches.
     * Null or empty JSON removes the UDS contribution. Parse failures propagate so profile and
     * delegation updates roll back together; unchanged values do not cause realm writes.
     */
    public static void sync(KeycloakSession session, RealmModel realm, String profilesJson) {
        Set<String> effective = configuredAllowedMappers(session, profilesJson);
        Set<String> manual = csvValues(realm.getAttribute(ALLOWED_MAPPERS_ATTRIBUTE));
        manual.removeAll(csvValues(realm.getAttribute(UDS_ADDED_MAPPERS_ATTRIBUTE)));

        Set<String> nextAllowed = new TreeSet<>(manual);
        nextAllowed.addAll(effective);
        Set<String> nextUdsAdded = new TreeSet<>(effective);
        nextUdsAdded.removeAll(manual);

        setIfChanged(realm, ALLOWED_MAPPERS_ATTRIBUTE, nextAllowed);
        setIfChanged(realm, UDS_ADDED_MAPPERS_ATTRIBUTE, nextUdsAdded);
    }

    /** Resolves every UDS executor through the same rules used by the client policy itself. */
    private static Set<String> configuredAllowedMappers(KeycloakSession session, String profilesJson) {
        if (profilesJson == null || profilesJson.isBlank()) {
            return Set.of();
        }
        ClientProfilesRepresentation profiles;
        try {
            profiles = JsonSerialization.readValue(profilesJson, ClientProfilesRepresentation.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read persisted client profiles", e);
        }
        if (profiles == null || profiles.getProfiles() == null) {
            return Set.of();
        }
        Set<String> allowed = new TreeSet<>();
        for (ClientProfileRepresentation profile : profiles.getProfiles()) {
            if (profile.getExecutors() == null) continue;
            for (ClientPolicyExecutorRepresentation executor : profile.getExecutors()) {
                if (!PROVIDER_ID.equals(executor.getExecutorProviderId())) continue;
                UDSClientPolicyPermissionsExecutorConfiguration configuration = new UDSClientPolicyPermissionsExecutorConfiguration();
                if (executor.getConfiguration() != null) {
                    JsonNode config = RawJsonValue.unwrap(JsonNode.class, executor.getConfiguration());
                    if (config != null && !config.isNull()) {
                        configuration = JsonSerialization.mapper.convertValue(config, UDSClientPolicyPermissionsExecutorConfiguration.class);
                    }
                }
                allowed.addAll(ProtocolMapperPolicy.resolveAllowed(configuration, session));
            }
        }
        return allowed;
    }

    /** Reads the native comma-separated format while ignoring whitespace and duplicate IDs. */
    private static Set<String> csvValues(String value) {
        Set<String> values = new TreeSet<>();
        if (value != null) {
            for (String item : value.split(",")) {
                if (!item.isBlank()) values.add(item.trim());
            }
        }
        return values;
    }

    /** Uses stable ordering to avoid writes when only profile ordering changes. */
    private static void setIfChanged(RealmModel realm, String attribute, Set<String> values) {
        String value = String.join(",", values);
        if (!value.equals(realm.getAttribute(attribute))) {
            realm.setAttribute(attribute, value);
        }
    }
}
