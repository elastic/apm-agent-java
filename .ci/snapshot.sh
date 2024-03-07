#!/usr/bin/env bash
##  This script runs the snapshot given the different environment variables
##    dry_run
##
##  It relies on the .buildkite/hooks/pre-command so the Vault and other tooling
##  are prepared automatically by buildkite.
##

set -eo pipefail

# Make sure we delete this folder before leaving even in case of failure
clean_up () {
  ARG=$?
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
if [[ "$dry_run" == "false" ]] ; then
  echo "--- Deploy the snapshot :package:"
  ./mvnw -V -s .ci/settings.xml -Pgpg clean deploy -DskipTests --batch-mode | tee snapshot.txt
else
  echo "--- Deploy the snapshot :package: (dry-run)"
  ./mvnw -V -s .ci/settings.xml -Pgpg clean install -DskipTests --batch-mode | tee snapshot.txt
fi
