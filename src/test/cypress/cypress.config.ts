/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { defineConfig } from "cypress";

const useCAC = process.env.USE_CAC === "true";

module.exports = defineConfig({
  clientCertificates: useCAC
    ? [
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
    ]
    : [],

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
    injectDocumentDomain: true,
  },

  pageLoadTimeout: 12000,
});
