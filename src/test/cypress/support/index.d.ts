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
  }
}
