import { RegistrationFormData } from "../support/types";

describe('Group Authorization', () => {
  it('Grafana Admin User - Success', () => {
    const formData: RegistrationFormData = {
      username: "testing_admin",
      password: "Testingpassword1!!",
    };

    // navigate to grafana dashboard
    cy.accessGrafana();

    // login admin user
    cy.loginUser(formData.username, formData.password);

    // skip x509 pop up
    cy.avoidX509();

    // Assert that admin user was able to access grafana dashboard
    cy.url().should('include', 'https://grafana.admin.uds.dev');
  });

  it('Grafana Auditor User - Failure', () => {
    const formData: RegistrationFormData = {
      username: "testing_user",
      password: "Testingpassword1!!",
    };

    // navigate to grafana dashboard
    cy.accessGrafana();

    // login user
    cy.loginUser(formData.username, formData.password);

    // Assert that the user couldn't access the grafana dashboard
    cy.url().should('include', 'https://sso.uds.dev');
    cy.contains('p.instruction', 'Your account has not been granted access to this application group yet.').should('be.visible');
  });
});