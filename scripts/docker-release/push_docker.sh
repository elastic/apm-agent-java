#!/usr/bin/env bash

# See full documentation in the "Creating and publishing Docker images" section
# of CONTRIBUTING.md

set -euxo pipefail

# This script is present on workers but may not be present in a development
# environment.

if [ ${WORKSPACE+x} ]  # We are on a CI worker
then
  source /usr/local/bin/bash_standard_lib.sh
fi

readonly RETRIES=3

# This script is intended to work in conjunction with the build_docker
# script. It assumes that build_docker.sh has been run at least once, thereby
# creating a Docker image to push. If this script does not detect an image
# to be uploaded, it will fail.

# Grab the tag we are working with

readonly RELEASE_VERSION=${1}
readonly DOCKER_REGISTRY_URL="docker.elastic.co"
readonly DOCKER_IMAGE_NAME="observability/apm-agent-java"
readonly DOCKER_PUSH_IMAGE="$DOCKER_REGISTRY_URL/$DOCKER_IMAGE_NAME:$RELEASE_VERSION"
readonly DOCKER_PUSH_IMAGE_LATEST="$DOCKER_REGISTRY_URL/$DOCKER_IMAGE_NAME:latest"

# Proceed with pushing to the registry
echo "INFO: Pushing image $DOCKER_PUSH_IMAGE to $DOCKER_REGISTRY_URL"

docker push $DOCKER_PUSH_IMAGE || { echo "You may need to run 'docker login' first and then re-run this script"; exit 1; }
docker push "${DOCKER_PUSH_IMAGE}-wolfi" || { echo "You may need to run 'docker login' first and then re-run this script"; exit 1; }

readonly LATEST_TAG=$(git tag --list --sort=version:refname "v*" | grep -v RC | sed s/^v// | tail -n 1)

if [ "$RELEASE_VERSION" = "$LATEST_TAG" ]
then
  echo "INFO: Current version ($RELEASE_VERSION) is the latest version. Tagging and pushing $DOCKER_PUSH_IMAGE_LATEST ..."
  docker tag $DOCKER_PUSH_IMAGE $DOCKER_PUSH_IMAGE_LATEST
  docker push $DOCKER_PUSH_IMAGE_LATEST || { echo "You may need to run 'docker login' first and then re-run this script"; exit 1; }
  docker tag "${DOCKER_PUSH_IMAGE}-wolfi" "${DOCKER_PUSH_IMAGE_LATEST}-wolfi"
  docker push "${DOCKER_PUSH_IMAGE_LATEST}-wolfi" || { echo "You may need to run 'docker login' first and then re-run this script"; exit 1; }
fi
