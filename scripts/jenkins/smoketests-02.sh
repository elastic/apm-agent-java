#!/usr/bin/env bash

set -euxo pipefail

./mvnw -q -Dmaven.javadoc.skip=true -am -amd -pl integration-tests,apm-agent-core,apm-agent-api -P integration-test-only clean compile verify
