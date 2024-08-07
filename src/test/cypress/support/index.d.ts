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

    verifyLoggedIn(firstname: string, lastname: string): Chainable;
  }
}
