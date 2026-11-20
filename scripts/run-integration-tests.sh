#!/usr/bin/env bash
# Copyright 2026 Defense Unicorns
# SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial

set -euo pipefail
umask 077
cd "$(dirname "${BASH_SOURCE[0]}")/.."
mkdir -p .cache/test-pki
run_dir="$(mktemp -d "$PWD/.cache/test-pki/run.XXXXXX")"
export UDS_TEST_PKI_DIR="$run_dir/pki"
trap 'rm -rf -- "$run_dir"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
uds run uds-core-integration-setup --no-progress
uds run integration-tests --no-progress
