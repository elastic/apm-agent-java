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
# Default in dry-run mode
GOAL="install"
DRY_RUN_MSG="(dry-run)"
# Otherwise, a snapshot
if [[ "$dry_run" == "false" ]] ; then
  GOAL="deploy"
  DRY_RUN_MSG=""
fi

echo "--- Deploy the snapshot :package: [./mvnw $GOAL)] $DRY_RUN_MSG"
./mvnw -V -s .ci/settings.xml -Pgpg clean $GOAL -DskipTests --batch-mode | tee snapshot.txt

echo "--- Archive the target folder with jar files"
find . -type d -name target -exec find {} -name '*.jar' -print0 \; | xargs -0 tar -cvf dist.tar
