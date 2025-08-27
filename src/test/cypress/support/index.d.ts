/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

/**
 * Custom Cypress Commands for reusable code blocks
 */

declare namespace Cypress {
  interface Exec {
    /**
     * Exit code of the command
     */
    exitCode: number;
    /**
     * A message when the exec cannot be parsed or the command errors
     */
    stderr: string;
    /**
     * Output to stdout by the command
     */
    stdout: string;
  }

  interface Chainable {
    loginPage(): Chainable;

    loginUser(username: string, password: string): Chainable;

    registrationPage(formData: any): Chainable;

    accessGrafana(): Chainable;

    avoidX509(): Chainable;

    verifyLoggedIn(): Chainable;

    getAccessToken(): Chainable;

    getClientSecret(clientId: string): Chainable<{ accessToken: string; clientSecret: string }>;

    /**
     * Retrieve and base64-decode a value from a Kubernetes Secret by key.
     * The "key" parameter is required and must be non-empty.
     */
    getValueFromSecret(namespace: string, secretName: string, key: string): Chainable<string>;
  }
}
