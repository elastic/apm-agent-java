#!/usr/bin/env bash

set -exo pipefail

BASE_URL=https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent
CF_FILE=cloudfoundry/index.yml

VERSION=${1:?"usage $0 <release_version>"}

# make script idempotent if release is already in CF descriptor
set +e
grep -e "^${VERSION}" ${CF_FILE}
[[ $? == 0 ]] && exit 0

set -e
echo "${VERSION}: ${BASE_URL}/${VERSION}/elastic-apm-agent-$VERSION.jar" >> ${CF_FILE}
git commit ${CF_FILE} -m "Update cloudfoundry for ${VERSION} release"
