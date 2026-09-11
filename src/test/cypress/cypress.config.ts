/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { exec } from "node:child_process";
import { defineConfig } from "cypress";

const useCAC = process.env.USE_CAC === "true";
const EXEC_PROCESS_TIMEOUT_MS = 305_000;
const TASK_TIMEOUT_GRACE_MS = 5_000;

type ExecTaskInput =
  | string
  | {
      command: string;
      failOnNonZeroExit?: boolean;
    };

interface ExecTaskResult {
  exitCode: number;
  stderr: string;
  stdout: string;
}

function runCommand(input: ExecTaskInput): Promise<ExecTaskResult> {
  const command = typeof input === "string" ? input : input.command;
  const failOnNonZeroExit = typeof input === "string" || input.failOnNonZeroExit !== false;

  return new Promise((resolve, reject) => {
    // Kill the process before Cypress reaches its task timeout so retries cannot
    // leave an earlier command running in the background.
    exec(
      command,
      { timeout: EXEC_PROCESS_TIMEOUT_MS, killSignal: "SIGTERM" },
      (error, stdout, stderr) => {
        const result = {
          exitCode: typeof error?.code === "number" ? error.code : error ? 1 : 0,
          stderr,
          stdout,
        };

        if (error && failOnNonZeroExit) {
          reject(
            new Error(`Command failed with exit code ${result.exitCode}: ${command}\n${stderr}`),
          );
          return;
        }

        resolve(result);
      },
    );
  });
}

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
    setupNodeEvents(on) {
      on("task", {
        exec: runCommand,
      });
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
  taskTimeout: EXEC_PROCESS_TIMEOUT_MS + TASK_TIMEOUT_GRACE_MS,
});
