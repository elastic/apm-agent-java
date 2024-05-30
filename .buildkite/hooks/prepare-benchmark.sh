#!/usr/bin/env bash
set -euo pipefail

echo "--- Prepare elasticsearch secrets :vault:"
ES_URL_SECRET=$(vault read -field=es_url secret/ci/elastic-apm-agent-java/opentelemetry-benchmark)
ES_USER_SECRET=$(vault read -field=es_user secret/ci/elastic-apm-agent-java/opentelemetry-benchmark)
ES_PASS_SECRET=$(vault read -field=es_pass secret/ci/elastic-apm-agent-java/opentelemetry-benchmark)
export ES_URL_SECRET ES_USER_SECRET ES_PASS_SECRET

echo "--- Install gh :github:"
GH_URL=https://github.com/cli/cli/releases/download/v2.37.0/gh_2.37.0_linux_amd64.tar.gz
GH_HOME=$(pwd)/.gh
curl -L --output /tmp/gh.tar.gz "$GH_URL"
mkdir -p "$GH_HOME"
tar --extract --file /tmp/gh.tar.gz --directory "$GH_HOME" --strip-components 1

PATH=$GH_HOME/bin:$PATH
export PATH
