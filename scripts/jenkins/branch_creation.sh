#!/usr/bin/env bash

set -xo pipefail

# Usage ./branch_creation 1.0.1

TMP_CHECKOUT=$(mktemp -d)
cd $TMP_CHECKOUT
git clone https://github.com/elastic/apm-agent-java
cd apm-agent-java

$(echo ${1}|cut -f2-3 -d '.'|{ read ver; test $ver == '0.0'; })
VER=$?

if [ $VER -eq 0 ];
then
echo "Found major release"
NEW_BRANCH_NAME=$(git tag|tail -1|sed s/v//|cut -f1 -d "."|awk '{print $1".x"}')
git checkout -b $NEW_BRANCH_NAME
git push
else
echo "Found minor release"
TAG=$(git tag|tail -1)
TARGET_BRANCH=$(git tag|tail -1|sed s/v//|cut -f1 -d '.'|awk '{print $1".x"}')
git branch -f $TARGET_BRANCH $TAG
git push
fi