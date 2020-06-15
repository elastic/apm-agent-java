#!/usr/bin/env bash

set -ex

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

cd $DIR/docs_release_check

docker build . -t docs_release_check
docker run docs_release_check --url https://www.elastic.co/guide/en/apm/agent/java/current/release-notes.html --release $1
