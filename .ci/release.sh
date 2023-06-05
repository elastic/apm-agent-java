#!/usr/bin/env bash
##  This script runs the release given the different environment variables
##    dry_run
##
##  It relies on the .buildkite/hooks/pre-command so the Vault and other tooling
##  are prepared automatically by buildkite.
##

set -eo pipefail

# Make sure we delete this folder before leaving even in case of failure
clean_up () {
  ARG=$?
  export VAULT_TOKEN=$PREVIOUS_VAULT_TOKEN
  echo "--- Deleting tmp workspace"
  rm -rf $TMP_WORKSPACE
  exit $ARG
}
trap clean_up EXIT

echo "--- Debug JDK installation :coffee:"
echo $JAVA_HOME
echo $PATH
java -version

set +x
echo "--- Deploy the release :package:"
if [[ "$dry_run" == "true" ]] ; then
  ./mvnw clean install -DskipTests=true -Dmaven.javadoc.skip=true | release.txt
else
  ./mvnw -V -s .ci/settings.xml -Pgpg clean deploy -DskipTests --batch-mode | tee release.txt
fi
