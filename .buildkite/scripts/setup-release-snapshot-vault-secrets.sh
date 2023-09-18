#!/usr/bin/env bash
#
# This script exports the vault secrets required for release and snapshot pipelines.
#
set -euo pipefail

echo "--- Prepare vault context :vault:"

VAULT_ROLE_ID_SECRET=$(vault read -field=role-id secret/ci/elastic-apm-agent-java/internal-ci-approle)
export VAULT_ROLE_ID_SECRET

VAULT_SECRET_ID_SECRET=$(vault read -field=secret-id secret/ci/elastic-apm-agent-java/internal-ci-approle)
export VAULT_SECRET_ID_SECRET

VAULT_ADDR=$(vault read -field=vault-url secret/ci/elastic-apm-agent-java/internal-ci-approle)
export VAULT_ADDR

# Delete the vault specific accessing the ci vault
PREVIOUS_VAULT_TOKEN=$VAULT_TOKEN
export PREVIOUS_VAULT_TOKEN
unset VAULT_TOKEN

echo "--- Prepare a secure temp :closed_lock_with_key:"
# Prepare a secure temp folder not shared between other jobs to store the key ring
export TMP_WORKSPACE=/tmp/secured
export KEY_FILE=$TMP_WORKSPACE"/private.key"

# Secure home for our keyring
export GNUPGHOME=$TMP_WORKSPACE"/keyring"
mkdir -p $GNUPGHOME
chmod -R 700 $TMP_WORKSPACE

echo "--- Prepare keys context :key:"
VAULT_TOKEN=$(vault write -field=token auth/approle/login role_id="$VAULT_ROLE_ID_SECRET" secret_id="$VAULT_SECRET_ID_SECRET")
export VAULT_TOKEN

# Nexus credentials
SERVER_USERNAME=$(vault read -field username secret/release/nexus)
export SERVER_USERNAME
SERVER_PASSWORD=$(vault read -field password secret/release/nexus)
export SERVER_PASSWORD

# Signing keys
vault read -field=key secret/release/signing >$KEY_FILE
KEYPASS_SECRET=$(vault read -field=passphrase secret/release/signing)
export KEYPASS_SECRET
export KEY_ID_SECRET=D88E42B4

# Import the key into the keyring
echo "$KEYPASS_SECRET" | gpg --batch --import "$KEY_FILE"
