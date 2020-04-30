#!/usr/bin/env bash

# Prepare a secure temp folder not shared between other jobs to store the key ring
export KEY_FILE=$TMP_WORKSPACE"/private.key"

# Secure home for our keyring
export GNUPGHOME="$WORKSPACE@tmp/keyring"

mkdir -p $GNUPGHOME
chmod 0700 $GNUPGHOME


#Make sure we delete this folder before leaving even in case of failure
clean_up () {
    ARG=$?
    unset KEY_FILE GNUPGHOME KEYPASS SERVER_USERNAME SERVER_PASSWORD
    echo "Deleting tmp workspace keyring"
    rm -rf $WORKSPACE@tmp/keyring
    echo "$WORKSPACE@tmp/keyring deleted"
    exit $ARG
}
trap clean_up EXIT

set +x

# Import the key into the keyring
echo $KEYPASS | gpg --batch --import keyfile > /dev/null 2>&1

set -x

# Deploy the snapshot
# Sensitive data may be logged to stderr. Remove for debugging.
$BASE_DIR/mvnw -s .ci/settings.xml -Pgpg clean deploy -DskipTests --batch-mode 2>/dev/null
