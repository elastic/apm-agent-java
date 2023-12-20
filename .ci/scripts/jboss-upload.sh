#!/usr/bin/env bash

# Bash strict mode
set -eo pipefail

## This script is called manually when a new Docker image is added.
while read -r i
do
  docker pull registry.access.redhat.com/jboss-eap-7/$i --platform linux/amd64
  docker tag registry.access.redhat.com/jboss-eap-7/$i docker.elastic.co/observability-ci/$i
  docker push docker.elastic.co/observability-ci/$i
done < .ci/scripts/jboss-docker-images.txt
