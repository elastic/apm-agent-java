#!/usr/bin/env bash

# We are looking for something like this:
# https://oss.sonatype.org/service/local/repositories/releases/content/co/elastic/apm/apm-agent-java/1.1.12/apm-agent-java-1.1.12.pom

# Requires that $TAG_VER be present in the environment

BASE_URL="https://oss.sonatype.org/service/local/repositories/releases/content/co/elastic/apm/apm-agent-java"
FULL_URL="$BASE_URL/$TAG_VER/apm-agent-java-$TAG_VER.pom"
echo $FULL_URL