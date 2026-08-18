/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.models.utils.FormMessage;

import java.util.List;

final class AlwaysSetPasswordValidationContext extends AlwaysSetPasswordFormContext implements ValidationContext {
    private final ValidationContext delegate;

    AlwaysSetPasswordValidationContext(final ValidationContext delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public void validationError(final MultivaluedMap<String, String> formData, final List<FormMessage> errors) {
        delegate.validationError(formData, errors);
    }

    @Override
    public void error(final String error) {
        delegate.error(error);
    }

    @Override
    public void success() {
        delegate.success();
    }

    @Override
    public void excludeOtherErrors() {
        delegate.excludeOtherErrors();
    }
}
