/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

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

  it('Grafana Auditor User - Success', () => {
    const formData: RegistrationFormData = {
      username: "testing_user",
      password: "Testingpassword1!!",
    };

    // navigate to grafana dashboard
    cy.accessGrafana();

    // login user
    cy.loginUser(formData.username, formData.password);

    // Assert that auditor user was able to access grafana dashboard
    cy.url().should('include', 'https://grafana.admin.uds.dev');
  });

  it('Grafana No Groups User - Failure', () => {
    const formData: RegistrationFormData = {
      username: "testing_user_no_groups",
      password: "Testingpassword1!!",
    };

    // navigate to grafana dashboard
    cy.accessGrafana();

    // login user
    cy.loginUser(formData.username, formData.password);

    // Assert that the user couldn't access the grafana dashboard
    cy.url().should('include', 'https://sso.uds.dev');
    // Matches uds_access_denied_client in messages_en.properties
    cy.contains('p.instruction', 'You do not have access to this application. Please contact your administrator.').should('be.visible');
  });

  it('Repeated group denials do not disable the account', () => {
    const formData: RegistrationFormData = {
      username: 'testing_user_no_groups',
      password: 'Testingpassword1!!',
    };

    // Attempt 1: denied by group authorization
    cy.accessGrafana();
    cy.loginUser(formData.username, formData.password);
    cy.url().should('include', 'https://sso.uds.dev');
    cy.contains('p.instruction', 'You do not have access to this application. Please contact your administrator.')
      .should('be.visible');

    // Attempt 2: denied again
    cy.accessGrafana();
    cy.loginUser(formData.username, formData.password);
    cy.url().should('include', 'https://sso.uds.dev');
    cy.contains('p.instruction', 'You do not have access to this application. Please contact your administrator.')
      .should('be.visible');

    // Attempt 3: denied again
    cy.accessGrafana();
    cy.loginUser(formData.username, formData.password);
    cy.url().should('include', 'https://sso.uds.dev');
    cy.contains('p.instruction', 'You do not have access to this application. Please contact your administrator.')
      .should('be.visible');

    // Prove account is not disabled/locked by logging into an allowed client (account console)
    cy.visit('https://sso.uds.dev/realms/uds/account/');
    cy.loginUser(formData.username, formData.password);
    // Confirm we are on the account console and the account is usable
    cy.url().should('include', '/realms/uds/account/');
    // Look for common tabs present in the Keycloak account console
    cy.contains(/Personal info|Account security|Applications|Sessions/i).should('be.visible');
  });
});
