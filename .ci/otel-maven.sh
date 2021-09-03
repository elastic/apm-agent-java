#!/usr/bin/env bash

set -x

git clone https://github.com/elastic/opentelemetry-maven-extension .otel
cd .otel || exit 1
cp ../mvnw* .
cp -rf ../.mvn .
ls -ltra
./mvnw clean install
cp target/opentelemetry-*.jar ../.mvn/opentelemetry-maven-extension.jar
cd .. || exit 1
rm -rf .otel
