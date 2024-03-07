#!/usr/bin/env bash
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
  echo "--- Deploy the release :package:"
  ./mvnw -V -s .ci/settings.xml -Pgpg clean deploy -DskipTests --batch-mode | tee release.txt
else
  echo "--- Deploy the release :package: (dry-run)"
  ./mvnw -V -s .ci/settings.xml -Pgpg clean install -DskipTests --batch-mode | tee release.txt
fi
