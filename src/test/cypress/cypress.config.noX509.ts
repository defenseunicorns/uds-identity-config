import { defineConfig } from "cypress";

module.exports = defineConfig({
  clientCertificates: [],

  env: {
    X509_CERT: false,
  },

  e2e: {
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
    retries: 3,
    specPattern: "e2e/noX509/**/*.cy.ts", // Pattern for tests that do not require the cert
    supportFolder: "support/",
    supportFile: "support/e2e.ts",
    screenshotOnRunFailure: false,
    video: false,
  },

  pageLoadTimeout: 12000,
});