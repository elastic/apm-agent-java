#!/usr/bin/env bash

set -euxo pipefail

./mvnw -q -Dmaven.javadoc.skip=true -P integration-test-only -P disable-application-integration-tests verify
