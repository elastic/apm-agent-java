#!/usr/bin/env bash

set -euxo pipefail

MOD=${1}
./mvnw -Dmaven.javadoc.skip=true -pl "${MOD}" -am compile verify
