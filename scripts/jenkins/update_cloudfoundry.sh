#!/usr/bin/env bash

set -exo pipefail

# Requires that TAG_VER already be present in the environment

TMP_CHECKOUT=$(mktemp -d)
BASE_URL=https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent


cd $TMP_CHECKOUT
git clone https://github.com/elastic/apm-agent-java
cd apm-agent-java

echo "$TAG_VER: $BASE_URL/$TAG/elastic-apm-agent-$TAG_VER.jar"

git push origin master
