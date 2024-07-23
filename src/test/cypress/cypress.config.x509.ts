import { defineConfig } from "cypress";

module.exports = defineConfig({
  clientCertificates: [
    {
      url: "https://sso.uds.dev/**",
      ca: [],
      certs: [
        {
          pfx: "certs/test.pfx",
          passphrase: "certs/pfx_passphrase.txt",
        },
      ],
    },
  ],

  env: {
    X509_CERT: true,
  },

  e2e: {
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
    retries: 3,
    specPattern: "e2e/x509/**/*.cy.ts", // Pattern for tests that require the cert
    supportFolder: "support/",
    supportFile: "support/e2e.ts",
    screenshotOnRunFailure: false,
    video: false,
  },

  pageLoadTimeout: 12000,
});