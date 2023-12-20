#!/usr/bin/env bash

# Bash strict mode
set -eo pipefail

## This script is used by the CI to be able to re-tag the docker images
## to use the official docker namespace.
## Or by an Elastic employee to configure the docker images as needed.
while read -r i ; do
  [[ -z $i ]] && continue
  name="${i##*/}"
  echo "::group::$name"
  docker pull docker.elastic.co/observability-ci/$name --platform linux/amd64
  docker tag docker.elastic.co/observability-ci/$name $i
  echo "::endgroup::"
done < .ci/scripts/jboss-docker-images.txt

if [ "$CI" == "true" ] ;then
  echo "::group::docker-images"
  docker images
  echo "::endgroup::"
fi