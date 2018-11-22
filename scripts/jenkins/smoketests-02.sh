#!/usr/bin/env bash

set -euxo pipefail

./mvnw -q -Dmaven.javadoc.skip=true -am -amd -pl integration-tests -P integration-test-only clean compile verify
