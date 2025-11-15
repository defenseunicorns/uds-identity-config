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

    registrationPage(formData: any, expectNewUser: boolean): Chainable;

    accessGrafana(): Chainable;

    avoidX509(): Chainable;

    verifyLoggedIn(): Chainable;

    /**
     * Retrieve an access token for Keycloak APIs.
     * - Default (or when subject is 'UDS_OPERATOR'): uses client credentials for the 'uds-operator' client
     * - When subject is 'KEYCLOAK_ADMIN': uses admin username/password from the 'keycloak-admin-password' Secret
     */
    getAccessToken(subject?: 'UDS_OPERATOR' | 'KEYCLOAK_ADMIN'): Chainable<string>;

    getClientSecret(clientId: string): Chainable<{ accessToken: string; clientSecret: string }>;

    /**
     * Delete a Keycloak user by username using the Admin API. This command is idempotent
     * and will not fail if the user is not found.
     */
    deleteUserByUsername(username: string): Chainable;

    /**
     * Retrieve and base64-decode a value from a Kubernetes Secret by key.
     * The "key" parameter is required and must be non-empty.
     */
    getValueFromSecret(namespace: string, secretName: string, key: string): Chainable<string>;
  }
}
