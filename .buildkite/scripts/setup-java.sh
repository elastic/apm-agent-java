#!/usr/bin/env bash
#
# This script installs the JDK17
#
set -euo pipefail

echo "--- Install JDK17 :java:"
# JDK version is defined in two different locations, here and .github/workflows/maven-goal/action.yml
JAVA_URL=https://jvm-catalog.elastic.co/jdk
JAVA_HOME=$(pwd)/.openjdk17
JAVA_PKG="$JAVA_URL/latest_openjdk_17_linux.tar.gz"
curl -sSL --output /tmp/jdk.tar.gz "$JAVA_PKG"
mkdir -p "$JAVA_HOME"
tar --extract --file /tmp/jdk.tar.gz --directory "$JAVA_HOME" --strip-components 1

export JAVA_HOME
PATH=$JAVA_HOME/bin:$PATH
export PATH

java -version || true
