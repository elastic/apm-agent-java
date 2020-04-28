#!/usr/bin/env bash

set -exo pipefail

TMP_CHECKOUT=$(mktemp -d)
BASE_URL=https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent


cd $TMP_CHECKOUT
git clone https://github.com/elastic/apm-agent-java
cd apm-agent-java

TAG=$(git tag | tail -1 | sed s/v//)
echo "$TAG: $BASE_URL/$TAG/elastic-apm-agent-$TAG.jar"

git push origin master
