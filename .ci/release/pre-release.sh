#!/usr/bin/env bash

# Bash strict mode
set -euo pipefail

# Found current script directory
RELATIVE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Found project directory
BASE_PROJECT="$(dirname $(dirname "${RELATIVE_DIR}"))"

# Import dependencies
source "${RELATIVE_DIR}/util.sh"

# Requirements
check_version "${RELEASE_VERSION}"

echo "Set release version"
./mvnw -V versions:set -DprocessAllModules=true -DgenerateBackupPoms=false -DnewVersion="${RELEASE_VERSION}"

echo "Prepare changelog for release"
java "${BASE_PROJECT}/.ci/ReleaseChangelog.java" CHANGELOG.next-release.md docs/release-notes "${RELEASE_VERSION}"
