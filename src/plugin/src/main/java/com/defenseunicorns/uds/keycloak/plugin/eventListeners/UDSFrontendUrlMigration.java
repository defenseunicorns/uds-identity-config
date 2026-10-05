/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.eventListeners;

import org.keycloak.models.RealmModel;

/** One-time cleanup of the UDS realm URL written by Identity Config 0.32.1. */
final class UDSFrontendUrlMigration {
    static final String FRONTEND_URL_ATTRIBUTE = "frontendUrl";
    static final String COMPLETION_ATTRIBUTE = "uds.identity-config.migrations.frontend-url-0.32.1";
    private static final String COMPLETED = "complete";
    private static final String UDS_REALM = "uds";

    enum Result {
        SKIPPED_ALREADY_COMPLETE,
        NOTHING_TO_REMOVE,
        REMOVED_GENERATED_VALUE,
        REVIEW_MISSING_DOMAIN,
        REVIEW_VALUE_MISMATCH
    }

    private UDSFrontendUrlMigration() {
    }

    static Result migrate(final RealmModel realm, final String domain) {
        if (COMPLETED.equals(realm.getAttribute(COMPLETION_ATTRIBUTE))) {
            return Result.SKIPPED_ALREADY_COMPLETE;
        }

        String currentValue = realm.getAttribute(FRONTEND_URL_ATTRIBUTE);
        if (currentValue == null || currentValue.isEmpty()) {
            realm.setAttribute(COMPLETION_ATTRIBUTE, COMPLETED);
            return Result.NOTHING_TO_REMOVE;
        }

        if (domain == null || domain.isBlank()) {
            realm.setAttribute(COMPLETION_ATTRIBUTE, COMPLETED);
            return Result.REVIEW_MISSING_DOMAIN;
        }

        String generatedValue = "https://sso." + domain.trim();
        if (generatedValue.equals(currentValue)) {
            realm.removeAttribute(FRONTEND_URL_ATTRIBUTE);
            realm.setAttribute(COMPLETION_ATTRIBUTE, COMPLETED);
            return Result.REMOVED_GENERATED_VALUE;
        }

        realm.setAttribute(COMPLETION_ATTRIBUTE, COMPLETED);
        return Result.REVIEW_VALUE_MISMATCH;
    }

    static boolean isUDSRealm(final RealmModel realm) {
        return UDS_REALM.equals(realm.getName());
    }
}
