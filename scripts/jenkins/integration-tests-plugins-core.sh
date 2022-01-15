#!/usr/bin/env bash

set -euxo pipefail

./mvnw -q -Dmaven.javadoc.skip=true -P disable-tests -P enable-plugin-integration-tests -P enable-core-integration-tests verify
