#!/usr/bin/env bash

set -euxo pipefail

MOD=$(find apm-agent-plugins -maxdepth 1 -mindepth 1 -type d|grep -v "target"|tr "\n" ",")

./mvnw -q -Dmaven.javadoc.skip=true -am -amd -pl ${MOD} -P integration-test-only verify
