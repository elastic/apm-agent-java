#!/usr/bin/env bash

# Bash strict mode
set -eo pipefail

cd apm-agent-core/src/test/resources/apm-server-schema/current
ls -l .
tar -xvzf $1 || true
ls -ltra .
