#!/usr/bin/env bash
#
# This script exports the vault secrets required for the load testing pipeline.
#
set -euo pipefail

VAULT_ROLE_ID_SECRET=$(vault kv get -field=vault_role_id kv/ci-shared/observability-ci/apm-ci-approle)
export VAULT_ROLE_ID_SECRET

VAULT_SECRET_ID_SECRET=$(vault kv get -field=vault_secret_id kv/ci-shared/observability-ci/apm-ci-approle)
export VAULT_SECRET_ID_SECRET

VAULT_ADDR=$(vault kv get -field=vault_addr kv/ci-shared/observability-ci/apm-ci-approle)
export VAULT_ADDR

# Delete the vault specific accessing the ci vault
PREVIOUS_VAULT_TOKEN=$VAULT_TOKEN
export PREVIOUS_VAULT_TOKEN
unset VAULT_TOKEN

echo "--- Prepare keys context :key:"
VAULT_TOKEN=$(vault write -field=token auth/approle/login role_id="$VAULT_ROLE_ID_SECRET" secret_id="$VAULT_SECRET_ID_SECRET")
export VAULT_TOKEN

APP_TOKEN_TYPE=$(vault read -field=user secret/apm-team/ci/bandstand)
export APP_TOKEN_TYPE
export APP_TOKEN_TYPE_SECRET="${APP_TOKEN_TYPE}" # This will mask the value in the logs
APP_TOKEN=$(vault read -field=password secret/apm-team/ci/bandstand)
export APP_TOKEN
