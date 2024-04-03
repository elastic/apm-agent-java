#!/usr/bin/env bash

# Bash strict mode
set -euo pipefail

# Found current script directory
RELATIVE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Found project directory
BASE_PROJECT="$(dirname $(dirname "${RELATIVE_DIR}"))"

# Import dependencies
source "${RELATIVE_DIR}/util.sh"

# Constants
BASE_URL="https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent"
CF_FILE="${BASE_PROJECT}/cloudfoundry/index.yml"

# Requirements
check_version "${RELEASE_VERSION}"

echo "Set next snapshot version"
./mvnw -V versions:set -DprocessAllModules=true -DgenerateBackupPoms=false -DnextSnapshot=true

# make script idempotent if release is already in CF descriptor
set +e
grep -e "^${RELEASE_VERSION}:" ${CF_FILE}
[[ $? == 0 ]] && exit 0
set -e
echo "Update cloudfoundry version"
echo "${RELEASE_VERSION}: ${BASE_URL}/${RELEASE_VERSION}/elastic-apm-agent-${RELEASE_VERSION}.jar" >> "${CF_FILE}"
