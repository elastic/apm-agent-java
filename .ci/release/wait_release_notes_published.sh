#!/usr/bin/env bash

set -euo pipefail
source "$(dirname "${0}")/util.sh"

# returns '0' (zero) status code when release notes have been published
# return non-zero status when they haven't been published yet or unable to execute request

check_version "${1:-}"
v="${1:-}"

full_url="https://www.elastic.co/docs/release-notes/apm/agents/java"
curl -fs "${full_url}" | grep ">${v}</a>"
