/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

/**
 * Custom Cypress Commands for reusable code blocks
 */

declare namespace Cypress {
  interface Chainable {
    loginPage(): Chainable;

    loginUser(username: string, password: string): Chainable;

    registrationPage(formData: any): Chainable;

    accessGrafana(): Chainable;

    avoidX509(): Chainable;

    verifyLoggedIn(): Chainable;

    getAccessToken(): Chainable;

    /**
     * Retrieve and base64-decode a value from a Kubernetes Secret by key.
     * The "key" parameter is required and must be non-empty.
     */
    getValueFromSecret(namespace: string, secretName: string, key: string): Chainable<string>;
  }
}
