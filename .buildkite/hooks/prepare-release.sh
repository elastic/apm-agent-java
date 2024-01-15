#!/usr/bin/env bash
set -euo pipefail

echo "--- Prepare a secure temp :closed_lock_with_key:"
# Prepare a secure temp folder not shared between other jobs to store the key ring
export TMP_WORKSPACE=/tmp/secured
export KEY_FILE=$TMP_WORKSPACE"/private.key"

# Secure home for our keyring
export GNUPGHOME=$TMP_WORKSPACE"/keyring"
mkdir -p $GNUPGHOME
chmod -R 700 $TMP_WORKSPACE

echo "--- Prepare keys context :key:"
# Nexus credentials
NEXUS_SECRET=kv/ci-shared/release-eng/team-release-secrets/apm/maven_central
SERVER_USERNAME=$(vault read -field username $NEXUS_SECRET)
export SERVER_USERNAME
SERVER_PASSWORD=$(vault read -field password $NEXUS_SECRET)
export SERVER_PASSWORD

# Signing keys
GPG_SECRET=kv/data/ci-shared/release-eng/team-release-secrets/apm/gpg
vault read -field=key_id $GPG_SECRET >$KEY_FILE
KEYPASS_SECRET=$(vault read -field=passphase $GPG_SECRET)
export KEYPASS_SECRET
export KEY_ID_SECRET=D88E42B4

# Import the key into the keyring
echo "$KEYPASS_SECRET" | gpg --batch --import "$KEY_FILE"

echo "--- Configure git context :git:"
# Configure the committer since the maven release requires to push changes to GitHub
# This will help with the SLSA requirements.
git config --global user.email "infra-root+apmmachine@elastic.co"
git config --global user.name "apmmachine"
