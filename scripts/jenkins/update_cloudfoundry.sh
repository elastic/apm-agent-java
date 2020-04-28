#!/usr/bin/env bash

set -exo pipefail

# Requires that TAG_VER already be present in the environment

BASE_URL=https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent
CF_FILE=cloudfoundry/index.yml

echo "$TAG_VER: $BASE_URL/$TAG/elastic-apm-agent-$TAG_VER.jar" >> $CF_FILE
git commit $CF_FILE -m "Update cloudfoundry for $TAG_VER release"