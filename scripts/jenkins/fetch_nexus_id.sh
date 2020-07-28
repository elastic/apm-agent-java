#!/usr/bin/env bash

set +x
export SERVER_USERNAME=$(vault read -field=staging-profile-id secret/apm-team/ci/nexus)
set -x
