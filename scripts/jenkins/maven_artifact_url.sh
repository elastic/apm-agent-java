#!/usr/bin/env bash

# We are looking for something like this:
# https://repo1.maven.org/maven2/co/elastic/apm/apm-agent-java/1.1.12/apm-agent-java-1.1.12.pom

# Requires that $TAG_VER be present in the environment

echo "https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/${TAG_VER}/elastic-apm-agent-${TAG_VER}.pom"
