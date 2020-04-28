#!/usr/bin/env bash

# We are looking for something like this:
# https://oss.sonatype.org/service/local/repositories/releases/content/co/elastic/apm/apm-agent-java/1.1.12/apm-agent-java-1.1.12.pom

BASE_URL="https://oss.sonatype.org/service/local/repositories/releases/content/co/elastic/apm/apm-agent-java"
TAG=$(git tag|tail -1|sed s/v//)
FULL_URL="$BASE_URL/$TAG/apm-agent-java-$TAG.pom"
echo $FULL_URL