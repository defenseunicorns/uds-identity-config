/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

import { spawn } from "node:child_process";
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

// Cypress 16 removed cy.exec(). Keep command execution in one Node-side task so
// specs share consistent exit handling and process cleanup without duplication.
function runCommand(input: ExecTaskInput): Promise<ExecTaskResult> {
  const command = typeof input === "string" ? input : input.command;
  const failOnNonZeroExit = typeof input === "string" || input.failOnNonZeroExit !== false;

  return new Promise((resolve, reject) => {
    const child = spawn(command, { detached: process.platform !== "win32", shell: true });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", data => (stdout += data));
    child.stderr.on("data", data => (stderr += data));
    // Kill the process before Cypress reaches its task timeout so retries cannot
    // leave an earlier command running in the background.
    const timeout = setTimeout(() => {
      try {
        if (child.pid && process.platform !== "win32") process.kill(-child.pid, "SIGTERM");
        else child.kill("SIGTERM");
      } catch {
        // The command already exited.
      }
    }, EXEC_PROCESS_TIMEOUT_MS);
    child.on("error", reject);
    child.on("close", exitCode => {
      clearTimeout(timeout);
      const result = { exitCode: exitCode ?? 1, stderr, stdout };
      if (result.exitCode && failOnNonZeroExit) {
        reject(new Error(`Command failed with exit code ${result.exitCode}: ${command}\n${stderr}`));
        return;
      }
      resolve(result);
    });
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
