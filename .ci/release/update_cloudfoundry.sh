#!/usr/bin/env bash

set -euo pipefail

REMOTE_NAME=origin
BRANCH_NAME=master
BASE_URL=https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent
CF_FILE=cloudfoundry/index.yml

v=${1:-}

if [[ "${v}" == "" ]]; then
  echo "usage $0 <version>"
  echo "where <version> in format '1.2.3'"
  exit 1
fi

if [[ ! "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "invalid version '${v}'"
  exit 1
fi

echo -e "\n--- fetch & ensure clean state of ${REMOTE_NAME}/${BRANCH_NAME}"
git fetch ${REMOTE_NAME} ${BRANCH_NAME}
git checkout ${BRANCH_NAME}
git reset --hard ${REMOTE_NAME}/${BRANCH_NAME}

echo -e "\n--- update ${CF_FILE} if required"

# make script idempotent if release is already in CF descriptor
set +e
grep -e "^${VERSION}" ${CF_FILE}
[[ $? == 0 ]] && exit 0
set -e

echo "${v}: ${BASE_URL}/${v}/elastic-apm-agent-${v}.jar" >> ${CF_FILE}
git commit ${CF_FILE} -m "Update cloudfoundry for ${v} release"
