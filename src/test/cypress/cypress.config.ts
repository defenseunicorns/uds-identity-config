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

  e2e: {
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
    retries: 3,
    specPattern: "e2e/**/*.cy.ts",
    supportFolder: "support/",
    supportFile: "support/e2e.ts",
    screenshotOnRunFailure: false,
    video: false,
  },

  pageLoadTimeout: 12000,
});
