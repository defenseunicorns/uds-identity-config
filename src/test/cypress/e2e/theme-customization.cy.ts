/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

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
        const backgroundJpg = /background\.jpg:\s*(.*)/.exec(configMap)?.[1];
        const faviconSvg = /favicon\.svg:\s*(.*)/.exec(configMap)?.[1];
        const footerPng = /footer\.png:\s*(.*)/.exec(configMap)?.[1];
        const logoSvg = /logo\.svg:\s*(.*)/.exec(configMap)?.[1];

        expect(backgroundJpg).to.exist;
        expect(faviconSvg).to.exist;
        expect(footerPng).to.exist;
        expect(logoSvg).to.exist;

        // logo.svg tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/logo.svg | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(logoSvg);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/uds-logo.svg | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(logoSvg);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/account/resources/public/logo.svg | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(logoSvg);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/account/resources/public/uds-logo.svg | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(logoSvg);
        });

        // background.jpg tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/tech-bg.jpg | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(backgroundJpg);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/account/resources/public/tech-bg.jpg | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(backgroundJpg);
        });

        // favicon.svg tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/favicon.svg | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(faviconSvg);
        });

        // footer.png tests
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/full-du-logo.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(footerPng);
        });
        cy.exec(
          "uds zarf tools kubectl exec keycloak-0 -n keycloak -- cat /opt/keycloak/themes/theme/login/resources/img/full-du-logo.png | base64 -w 0",
        ).then(result => {
          expect(result.stdout).to.equal(footerPng);
        });
      },
    );
  });
});
