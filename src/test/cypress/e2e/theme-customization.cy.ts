/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { RegistrationFormData } from "../support/types";

describe("Theme customizations", () => {
  it("Customization ConfigMap exists", () => {
    cy.exec("uds zarf tools kubectl get cm -n keycloak keycloak-theme-overrides").then(result => {
      expect(result.stdout).to.contain("keycloak-theme-overrides");
    });
  });

  it("UDS Identity Config has proper Volume Mounts", () => {
    cy.exec(
      "uds zarf tools kubectl get pod keycloak-0 -n keycloak -o yaml -o jsonpath='{.spec.initContainers[?(@.name==\"uds-config\")].volumeMounts}'",
    ).then(result => {
      expect(result.stdout).to.contain("theme-overrides");
    });
  });

  it("Override files are properly copied", () => {
    cy.exec("uds zarf tools kubectl get cm -n keycloak keycloak-theme-overrides -o yaml").then(
      result => {
        const configMap = result.stdout;
        const backgroundPng = /background\.png:\s*(.*)/.exec(configMap)?.[1];
        const faviconPng = /favicon\.png:\s*(.*)/.exec(configMap)?.[1];
        const footerPng = /footer\.png:\s*(.*)/.exec(configMap)?.[1];
        const logoPng = /logo\.png:\s*(.*)/.exec(configMap)?.[1];

        expect(backgroundPng).to.exist;
        expect(faviconPng).to.exist;
        expect(footerPng).to.exist;
        expect(logoPng).to.exist;

        // logo.png tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/logo.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(logoPng);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/account/resources/public/logo.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(logoPng);
        });

        // background.png tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/background.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(backgroundPng);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/account/resources/public/background.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(backgroundPng);
        });

        // favicon.png tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/favicon.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(faviconPng);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/account/resources/public/favicon.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(faviconPng);
        });

        // footer.png tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/footer.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(footerPng);
        });
      },
    );
  });

  it("Existing User", () => {
    // existing user
    const formData: RegistrationFormData = {
      firstName: "Testing",
      lastName: "User",
      username: "testing_user",
      password: "Testingpassword1!!",
    };

    cy.loginPage();
    cy.loginUser(formData.username, formData.password);

      cy.exec("uds zarf tools kubectl get cm -n keycloak keycloak-theme-overrides -o yaml").then(
        result => {
          const configMap = result.stdout;

          const text1 = /text1:\s*(.*)/.exec(configMap)?.[1];
          const decodedText1 = Buffer.from(text1, 'base64').toString('utf-8').trim();

          const text2 = /text2:\s*(.*)/.exec(configMap)?.[1];
          const decodedText2 = Buffer.from(text2, 'base64').toString('utf-8').trim();

          const text3 = /text3:\s*(.*)/.exec(configMap)?.[1];
          const decodedText3 = Buffer.from(text3, 'base64').toString('utf-8').trim();

          //ensure the navigation has finished
          cy.contains("h3", "Update to User Agreement").should("be.visible");
          cy.contains("h4", decodedText1).should("be.visible");
          cy.contains("h5", decodedText2).should("be.visible");
          cy.contains(decodedText3).should("be.visible");
        })
  });

});
