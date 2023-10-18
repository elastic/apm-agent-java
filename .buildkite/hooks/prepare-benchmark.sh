#!/usr/bin/env bash
set -euo pipefail

echo "--- Prepare github secrets :vault:"
GITHUB_SECRET=$(vault kv get -field token "kv/ci-shared/observability-ci/github-apmmachine")
GH_TOKEN=$GITHUB_SECRET
export GITHUB_SECRET GH_TOKEN
GITHUB_USERNAME=apmmachine
export GITHUB_USERNAME

echo "--- Install gh :github:"
GH_URL=https://github.com/cli/cli/releases/download/v2.37.0/gh_2.37.0_linux_amd64.tar.gz
GH_HOME=$(pwd)/.gh
curl -L --output /tmp/gh.tar.gz "$GH_URL"
mkdir -p "$GH_HOME"
tar --extract --file /tmp/gh.tar.gz --directory "$GH_HOME" --strip-components 1

PATH=$GH_HOME/bin:$PATH
export PATH

echo "--- Configure gh :github:"
echo "$GH_TOKEN" | gh auth login --with-token
