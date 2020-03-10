#!/usr/bin/env bash

## This script generates a URL for the release notes for this project
## It expects one argument, in the form of a release number.
##
## ex: generate_release_notes_url.sh 1.0.0
set -euxo pipefail

RELEASE_VERSION=$1

readonly BASE_URL="https://www.elastic.co/guide/en/apm/agent/java/current/release-notes-"

dotX=`echo $RELEASE_VERSION|cut -f1 -d "."|awk '{print \$1".x"}'`
echo $BASE_URL$dotX.html#release-notes-$RELEASE_VERSION
