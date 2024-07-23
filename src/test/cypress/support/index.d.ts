/**
 * Custom Cypress Commands for reusable code blocks
 */

declare namespace Cypress {
  interface Chainable {
    loginPage(): Chainable;

    loginUser(username: string, password: string): Chainable;

    registrationPage(): Chainable;

    registerUser(formData: any): Chainable;

    accessGrafana(): Chainable;

    avoidX509(): Chainable;

    setupOTP(username: string): Chainable;

    enterOTP(username: string): Chainable;

    verifyUserAccountPage(formData: any): Chainable;
  }
}
