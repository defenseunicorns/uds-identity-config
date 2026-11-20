#!/usr/bin/env python3
# Copyright 2026 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

"""Validate the generated PKI and its integration boundaries without a cluster."""

import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def run(*args, env=None, input=None, cwd=ROOT, check=True):
    return subprocess.run(args, cwd=cwd, env=env, input=input, text=True,
                          capture_output=True, check=check)


def openssl(*args, input=None):
    return run("openssl", *map(str, args), input=input).stdout


def fingerprint(pem):
    return openssl("x509", "-noout", "-fingerprint", "-sha256", input=pem)


class TestPKI(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        parent = ROOT / ".cache/test-pki"
        parent.mkdir(parents=True, exist_ok=True)
        cls.temp = tempfile.TemporaryDirectory(prefix="validation.", dir=parent)
        cls.work = Path(cls.temp.name)
        cls.pki = cls.work / "credentials with spaces"
        cls.env = dict(os.environ, UDS_TEST_PKI_DIR=str(cls.pki))
        run("bash", "scripts/generate-test-pki.sh", env=cls.env)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_client_chain_and_identity(self):
        result = openssl("verify", "-purpose", "sslclient", "-CAfile", self.pki / "ca.cer",
                         self.pki / "client.cer")
        self.assertIn(": OK", result)
        subject = openssl("x509", "-in", self.pki / "client.cer", "-noout", "-subject", "-nameopt", "RFC2253")
        self.assertEqual(subject.strip(), "subject=CN=UNICORN.DOUG.ROCKSTAR.1234567890,OU=PKI,O=U.S. Government,C=US")
        text = openssl("x509", "-in", self.pki / "client.cer", "-noout", "-text")
        for value in ("TLS Web Client Authentication", "2.16.840.1.101.3.6.8",
                      "2.16.840.1.101.3.2.1.3.12", "2.16.840.1.101.3.6.6",
                      "test@mil", "email:doug.unicorn@uds.dev", "Digital Signature"):
            self.assertIn(value, text)

    def test_pfx_key_and_shared_ca(self):
        def extract(*args):
            return openssl("pkcs12", "-in", self.pki / "test.pfx", "-passin",
                           f"file:{self.pki / 'pfx_passphrase.txt'}", *args)
        key = extract("-nocerts", "-nodes")
        client = extract("-clcerts", "-nokeys")
        ca = extract("-cacerts", "-nokeys")
        self.assertEqual(openssl("pkey", "-pubout", input=key),
                         openssl("x509", "-pubkey", "-noout", input=client))
        self.assertEqual(fingerprint(client), fingerprint((self.pki / "client.cer").read_text()))
        expected = fingerprint((self.pki / "ca.cer").read_text())
        self.assertEqual(fingerprint(ca), expected)
        with zipfile.ZipFile(self.pki / "authorized_certs.zip") as archive:
            self.assertEqual(archive.namelist(), ["ca.cer"])
            self.assertEqual(fingerprint(archive.read("ca.cer").decode()), expected)
        gateway = (self.pki / "tls_cacert.yaml").read_text()
        encoded = re.fullmatch(r'tls:\n  cacert: "([A-Za-z0-9+/=]+)"\n', gateway).group(1)
        self.assertEqual(fingerprint(base64.b64decode(encoded).decode()), expected)
        digest = hashlib.sha256((self.pki / "authorized_certs.zip").read_bytes()).hexdigest()
        self.assertEqual((self.pki / "authorized_certs.zip.sha256").read_text().strip(), digest)

    def test_node_can_load_pfx_with_exact_passphrase_file(self):
        result = run("node", "-e", """
const fs = require('node:fs');
const path = require('node:path');
require('node:tls').createSecureContext({
  pfx: fs.readFileSync(path.join(process.env.UDS_TEST_PKI_DIR, 'test.pfx')),
  passphrase: fs.readFileSync(path.join(process.env.UDS_TEST_PKI_DIR, 'pfx_passphrase.txt'), 'utf8'),
});
""", env=self.env)
        self.assertEqual(result.returncode, 0)

    def test_gateway_task_uses_generated_ca(self):
        task_file = json.loads(run("uds", "zarf", "tools", "yq", "-o=json", ".", "tasks.yaml").stdout)
        task = next(task for task in task_file["tasks"] if task["name"] == "uds-core-gateway-cacert")
        workspace = self.work / "gateway"
        values = workspace / "uds-core/src/istio/values"
        values.mkdir(parents=True)
        for name in ("config-tenant.yaml", "config-admin.yaml"):
            (values / name).write_text('tls:\n  key: keep-me\n  cacert: old-ca\n')
        run("bash", "-c", task["actions"][0]["cmd"], env=self.env, cwd=workspace)
        expected = fingerprint((self.pki / "ca.cer").read_text())
        for name in ("config-tenant.yaml", "config-admin.yaml"):
            config = json.loads(run("uds", "zarf", "tools", "yq", "-o=json", ".", str(values / name)).stdout)
            self.assertEqual(config["tls"]["key"], "keep-me")
            self.assertEqual(fingerprint(base64.b64decode(config["tls"]["cacert"]).decode()), expected)

    @unittest.skipUnless((ROOT / "src/test/cypress/node_modules/.bin/cypress").exists(), "Install Cypress dependencies to validate its runtime config")
    def test_cypress_runtime_config(self):
        spec = self.work / "pki-config.cy.js"
        # A real runner startup catches certificate-path validation before deployment.
        spec.write_text("it('loads generated PKI configuration', () => {});\n")
        result = run(str(ROOT / "src/test/cypress/node_modules/.bin/cypress"), "run",
                     "--spec", str(spec), "--config", f"specPattern={spec},supportFile=false",
                     env=dict(self.env, USE_CAC="true"), cwd=ROOT / "src/test/cypress", check=False)
        self.assertEqual(result.returncode, 0, (result.stdout + result.stderr)[-6000:])

    def test_permissions_and_git_ignore(self):
        self.assertEqual(self.pki.stat().st_mode & 0o777, 0o700)
        for name in ("ca.key", "client.key", "test.pfx", "pfx_passphrase.txt"):
            self.assertEqual((self.pki / name).stat().st_mode & 0o777, 0o600)
        self.assertEqual(run("git", "status", "--porcelain", "--untracked-files=all", "--", str(self.pki)).stdout, "")
        self.assertEqual(run("git", "check-ignore", str(self.pki / "test.pfx")).returncode, 0)

    def test_repeat_and_concurrent_generation(self):
        before = (self.pki / "ca.cer").read_bytes()
        result = run("bash", "scripts/generate-test-pki.sh", env=self.env, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(before, (self.pki / "ca.cer").read_bytes())
        paths = [self.work / f"parallel-{i}" for i in range(2)]
        def generate(path):
            run("bash", "scripts/generate-test-pki.sh", env=dict(self.env, UDS_TEST_PKI_DIR=str(path)))
            return (path / "ca.cer").read_bytes()
        with ThreadPoolExecutor(max_workers=2) as executor:
            certs = list(executor.map(generate, paths))
        self.assertNotEqual(certs[0], certs[1])
        self.assertNotIn(before, certs)

    def test_failed_generation_cleans_partial_credentials(self):
        bin_dir = self.work / "failed-bin"
        bin_dir.mkdir()
        stub = bin_dir / "openssl"
        stub.write_text('#!/bin/sh\ntouch ca.key\nexit 9\n')
        stub.chmod(0o700)
        output = self.work / "failed-output"
        env = dict(self.env, UDS_TEST_PKI_DIR=str(output), PATH=f"{bin_dir}:{os.environ['PATH']}")
        self.assertNotEqual(run("bash", "scripts/generate-test-pki.sh", env=env, check=False).returncode, 0)
        self.assertFalse(output.exists())

    def test_docker_arguments_and_cleanup(self):
        bin_dir = self.work / "docker-bin"
        bin_dir.mkdir()
        stub = bin_dir / "docker"
        # Inspect the actual staged archive while the Docker invocation owns it.
        stub.write_text('''#!/usr/bin/env python3
import hashlib, json, os, pathlib, sys, zipfile
args = sys.argv[1:]
values = [args[i + 1] for i, arg in enumerate(args) if arg == '--build-arg']
values = dict(item.split('=', 1) for item in values)
archive = pathlib.Path('src/truststore') / values['CA_ZIP_URL']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == values['CA_ZIP_SHA256']
with zipfile.ZipFile(archive) as source:
    assert source.namelist() == ['ca.cer']
pathlib.Path(os.environ['DOCKER_CAPTURE']).write_text(json.dumps({'args': args, 'archive': str(archive)}))
sys.exit(int(os.environ.get('DOCKER_EXIT', '0')))
''')
        stub.chmod(0o700)
        capture = self.work / "docker.json"
        env = dict(self.env, PATH=f"{bin_dir}:{os.environ['PATH']}", DOCKER_CAPTURE=str(capture))
        for exit_code in ("0", "42"):
            result = run("bash", "scripts/build-test-image.sh", env=dict(env, DOCKER_EXIT=exit_code), check=False)
            self.assertEqual(result.returncode, int(exit_code), result.stderr)
            data = json.loads(capture.read_text())
            self.assertIn("CA_ZIP_SHA256=" + (self.pki / "authorized_certs.zip.sha256").read_text().strip(), data["args"])
            self.assertFalse((ROOT / data["archive"]).exists())
        damaged = self.work / "damaged"
        shutil.copytree(self.pki, damaged)
        with (damaged / "authorized_certs.zip").open("ab") as archive:
            archive.write(b"corruption")
        capture.unlink()
        result = run("bash", "scripts/build-test-image.sh", env=dict(env, UDS_TEST_PKI_DIR=str(damaged)), check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(capture.exists())

    def test_run_wrapper_environment_order_and_cleanup(self):
        bin_dir = self.work / "uds-bin"
        bin_dir.mkdir()
        stub = bin_dir / "uds"
        stub.write_text('''#!/usr/bin/env python3
import json, os, pathlib, subprocess, sys
pki = pathlib.Path(os.environ['UDS_TEST_PKI_DIR'])
with open(os.environ['UDS_CAPTURE'], 'a') as log:
    log.write(json.dumps([sys.argv[1:], str(pki)]) + '\\n')
if sys.argv[2] == 'uds-core-integration-setup':
    subprocess.run(['bash', 'scripts/generate-test-pki.sh'], check=True)
else:
    assert (pki / '.complete').is_file()
sys.exit(7 if sys.argv[2] == os.environ.get('UDS_FAIL_TASK') else 0)
''')
        stub.chmod(0o700)
        capture = self.work / "uds.jsonl"
        env = dict(self.env, PATH=f"{bin_dir}:{os.environ['PATH']}", UDS_CAPTURE=str(capture))
        for fail_task in ("", "uds-core-integration-setup", "integration-tests"):
            capture.unlink(missing_ok=True)
            result = run("bash", "scripts/run-integration-tests.sh", env=dict(env, UDS_FAIL_TASK=fail_task), check=False)
            self.assertEqual(result.returncode, 7 if fail_task else 0, result.stderr)
            calls = [json.loads(line) for line in capture.read_text().splitlines()]
            self.assertEqual(len(calls), 1 if fail_task == "uds-core-integration-setup" else 2)
            self.assertEqual(calls[0][0][1], "uds-core-integration-setup")
            if len(calls) == 2:
                self.assertEqual(calls[1][0][1], "integration-tests")
                self.assertEqual(calls[0][1], calls[1][1])
            self.assertFalse(Path(calls[0][1]).parent.exists())

    @unittest.skipUnless(os.environ.get("TEST_PKI_DOCKER_BUILD") == "true", "Set TEST_PKI_DOCKER_BUILD=true to build the truststore")
    def test_real_truststore_build(self):
        image = f"uds-test-pki:truststore-{self.work.name}"
        try:
            result = run("bash", "scripts/build-test-image.sh", "--target", "truststore", "--tag", image, env=self.env, check=False)
            self.assertEqual(result.returncode, 0, result.stderr[-6000:])
            pem = run("docker", "run", "--rm", "--entrypoint", "cat", image,
                      "/home/build/authorized_certs.pem").stdout
            self.assertEqual(fingerprint(pem), fingerprint((self.pki / "ca.cer").read_text()))
        finally:
            run("docker", "image", "rm", image, check=False)


if __name__ == "__main__":
    unittest.main(verbosity=2)
