#!/usr/bin/env bash

set -xo pipefail

# Usage ./branch_creation 1.0.1

# Requires that $TAG_VER already be present in the env

TMP_CHECKOUT=$(mktemp -d)
cd $TMP_CHECKOUT
git clone https://github.com/elastic/apm-agent-java
cd apm-agent-java

$(echo ${1}|cut -f2-3 -d '.'|{ read ver; test $ver == '0.0'; })
VER=$?

if [ $VER -eq 0 ];
then
echo "Found major release"
git checkout -b $TAG_DOT_X
git push
else
echo "Found minor release"
git branch -f $TAG_DOT_X $TAG_VER
git push
fi
