#!/usr/bin/env bash
set -euo pipefail

GITHUB_SECRET=$(vault kv get -field token "kv/ci-shared/observability-ci/github-apmmachine")
export GITHUB_SECRET
GITHUB_USERNAME=apmmachine
export GITHUB_USERNAME
