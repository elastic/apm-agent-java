#!/usr/bin/env bash

# Bash strict mode
set -eo pipefail

## This script is used by the CI to be able to re-tag the docker images
## to use the official docker namespace.
## Or by an Elastic employee to configure the docker images as needed.
while read -r i ; do
  name="${i%%*/}"
  docker pull docker.elastic.co/observability-ci/$name --platform linux/amd64
  docker tag docker.elastic.co/observability-ci/$name $i
done < .ci/scripts/jboss-docker-images.txt
