#!/usr/bin/env bash

# Bash strict mode
set -eo pipefail

## This script is called manually when a new Docker image is added.
while read -r i ; do
  name="${i##*/}"
  docker pull --platform linux/amd64 $i
  docker tag $i docker.elastic.co/observability-ci/$name
  docker push docker.elastic.co/observability-ci/$name
done < .ci/scripts/jboss-docker-images.txt
