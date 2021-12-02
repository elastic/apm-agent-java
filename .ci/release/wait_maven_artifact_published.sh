#!/usr/bin/env bash

set -euo pipefail
source "$(dirname "${0}")/util.sh"

# returns '0' (zero) status code when artifact exists
# return non-zero status when artifact does not exists or unable to execute request

check_version "${1:-}"
v="${1:-}"

full_url="https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/${v}/elastic-apm-agent-${v}.pom"
curl -fs "${full_url}" 2>&1 > /dev/null
