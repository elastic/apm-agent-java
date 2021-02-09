#!/usr/bin/env bash

export VAULT_TOKEN=$(vault write -field=token auth/approle/login role_id="$VAULT_ROLE_ID" secret_id="$VAULT_SECRET_ID")
vault read -field=staging-profile-id secret/apm-team/ci/nexus
