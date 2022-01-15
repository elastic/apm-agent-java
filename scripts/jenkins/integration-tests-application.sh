#!/usr/bin/env bash

set -euxo pipefail

./mvnw -q -Dmaven.javadoc.skip=true -P disable-tests -P enable-application-integration-tests verify
