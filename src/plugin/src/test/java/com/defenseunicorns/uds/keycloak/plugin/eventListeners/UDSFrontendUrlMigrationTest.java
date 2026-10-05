/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-DefenseUnicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.junit.jupiter.api.Test;
import org.keycloak.models.RealmModel;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UDSFrontendUrlMigrationTest {
    private static final String DOMAIN = "uds.dev";
    private static final String GENERATED_URL = "https://sso.uds.dev";

    @Test
    void removesOnlyTheMatchingGeneratedValue() {
        Map<String, String> udsAttributes = new HashMap<>(Map.of(
                UDSFrontendUrlMigration.FRONTEND_URL_ATTRIBUTE, GENERATED_URL,
                "operatorSetting", "keep-me"));
        RealmModel udsRealm = realm("uds", udsAttributes);
        Map<String, String> masterAttributes = new HashMap<>(Map.of("frontendUrl", "https://keycloak.admin.uds.dev"));
        RealmModel masterRealm = realm("master", masterAttributes);

        assertEquals(UDSFrontendUrlMigration.Result.REMOVED_GENERATED_VALUE,
                UDSFrontendUrlMigration.migrate(udsRealm, DOMAIN));

        assertNull(udsAttributes.get("frontendUrl"));
        assertEquals("keep-me", udsAttributes.get("operatorSetting"));
        assertEquals("complete", udsAttributes.get(UDSFrontendUrlMigration.COMPLETION_ATTRIBUTE));
        assertEquals("https://keycloak.admin.uds.dev", masterAttributes.get("frontendUrl"));
        verify(masterRealm, never()).removeAttribute(anyString());
        verify(masterRealm, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void marksAnAbsentValueCompleteWithoutChangingOtherAttributes() {
        Map<String, String> attributes = new HashMap<>(Map.of("operatorSetting", "keep-me"));
        RealmModel udsRealm = realm("uds", attributes);

        assertEquals(UDSFrontendUrlMigration.Result.NOTHING_TO_REMOVE,
                UDSFrontendUrlMigration.migrate(udsRealm, DOMAIN));

        assertNull(attributes.get("frontendUrl"));
        assertEquals("keep-me", attributes.get("operatorSetting"));
        assertEquals("complete", attributes.get(UDSFrontendUrlMigration.COMPLETION_ATTRIBUTE));
        verify(udsRealm, never()).removeAttribute("frontendUrl");
    }

    @Test
    void preservesCustomAndOlderDomainValuesForManualReview() {
        for (String currentValue : new String[] {"https://login.example.test", "https://sso.older.example.test"}) {
            Map<String, String> attributes = new HashMap<>(Map.of("frontendUrl", currentValue, "other", "unchanged"));
            RealmModel udsRealm = realm("uds", attributes);

            assertEquals(UDSFrontendUrlMigration.Result.REVIEW_VALUE_MISMATCH,
                    UDSFrontendUrlMigration.migrate(udsRealm, DOMAIN));

            assertEquals(currentValue, attributes.get("frontendUrl"));
            assertEquals("unchanged", attributes.get("other"));
            assertEquals("complete", attributes.get(UDSFrontendUrlMigration.COMPLETION_ATTRIBUTE));
            verify(udsRealm, never()).removeAttribute("frontendUrl");
        }
    }

    @Test
    void preservesValueAndMarksForReviewWhenDomainIsMissing() {
        Map<String, String> attributes = new HashMap<>(Map.of("frontendUrl", GENERATED_URL, "other", "unchanged"));
        RealmModel udsRealm = realm("uds", attributes);

        assertEquals(UDSFrontendUrlMigration.Result.REVIEW_MISSING_DOMAIN,
                UDSFrontendUrlMigration.migrate(udsRealm, null));

        assertEquals(GENERATED_URL, attributes.get("frontendUrl"));
        assertEquals("unchanged", attributes.get("other"));
        assertEquals("complete", attributes.get(UDSFrontendUrlMigration.COMPLETION_ATTRIBUTE));
        verify(udsRealm, never()).removeAttribute("frontendUrl");
    }

    @Test
    void doesNotRepeatAfterAnOperatorSetsTheGeneratedUrlLater() {
        Map<String, String> attributes = new HashMap<>(Map.of("frontendUrl", GENERATED_URL));
        RealmModel udsRealm = realm("uds", attributes);

        assertEquals(UDSFrontendUrlMigration.Result.REMOVED_GENERATED_VALUE,
                UDSFrontendUrlMigration.migrate(udsRealm, DOMAIN));
        attributes.put("frontendUrl", GENERATED_URL);

        assertEquals(UDSFrontendUrlMigration.Result.SKIPPED_ALREADY_COMPLETE,
                UDSFrontendUrlMigration.migrate(udsRealm, DOMAIN));
        assertEquals(GENERATED_URL, attributes.get("frontendUrl"));
        verify(udsRealm, org.mockito.Mockito.times(1)).removeAttribute("frontendUrl");
    }

    private static RealmModel realm(final String name, final Map<String, String> attributes) {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn(name);
        when(realm.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            attributes.remove(invocation.getArgument(0));
            return null;
        }).when(realm).removeAttribute(anyString());
        org.mockito.Mockito.doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(realm).setAttribute(anyString(), anyString());
        return realm;
    }
}
