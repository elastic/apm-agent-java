#!/usr/bin/env bash

set -exo pipefail

# Requires that TAG_VER already be present in the environment

BASE_URL=https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent
CF_FILE=cloudfoundry/index.yml

# make script idempotent if release is already in CF descriptor
set +e
grep -e "^${TAG_VER}" ${CF_FILE}
[[ $? == 0 ]] && exit 0

set -e
echo "${TAG_VER}: ${BASE_URL}/${TAG_VER}/elastic-apm-agent-$TAG_VER.jar" >> ${CF_FILE}
git commit ${CF_FILE} -m "Update cloudfoundry for ${TAG_VER} release"
