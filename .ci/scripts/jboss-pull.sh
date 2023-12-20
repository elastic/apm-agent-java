#!/usr/bin/env bash

# Bash strict mode
set -eo pipefail

# Retry the given command for the given times.
retry() {
  local retries=$1
  shift
  local count=0
  until "$@"; do
    exit=$?
    wait=$((2 ** count))
    count=$((count + 1))
    if [ $count -lt "$retries" ]; then
      sleep $wait
    else
      return $exit
    fi
  done
  return 0
}

## This script is used by the CI to be able to re-tag the docker images
## to use the official docker namespace.
## Or by an Elastic employee to configure the docker images as needed.
while read -r i ; do
  [[ -z $i ]] && continue
  name="${i##*/}"
  echo "::group::$name"
  retry 3 docker pull --platform linux/amd64 docker.elastic.co/observability-ci/$name
  docker tag docker.elastic.co/observability-ci/$name $i
  echo "::endgroup::"
done < .ci/scripts/jboss-docker-images.txt

if [ "$CI" == "true" ] ;then
  echo "::group::docker-images"
  docker images
  echo "::endgroup::"
fi