#!/bin/bash
set -euxo pipefail

# This script is intended to work in conjunction with the build_docker 
# script. It assumes that build_docker.sh has been run at least once, thereby
# creating a Docker image to push. If this script does not detect an image
# to be uploaded, it will fail.

# This script is intended to be run from a CI job and will not work if run in
# standalone manner unless certain envrionment variables are set.

# 1. Grab the tag we are working with

#FIXME DRY between Docker scripts
echo "INFO: Fetching latest tag"
#TODO Move this into apm-pipeline-library
CUR_TAG_DEFAULT=$(git describe --abbrev=0|sed s/^v//)
readonly CUR_TAG=${CUR_TAG:-$CUR_TAG_DEFAULT}

# 2. Construct the image:tag that we are working with
# This is roughly <repo>/<namespace>/image
readonly DOCKER_PUSH_IMAGE="docker.elastic.co/apm/apm-agent-java:$CUR_TAG"

# 3. Proceed with pushing to the registry
readonly DOCKER_REGISTRY_URL=`echo $DOCKER_PUSH_IMAGE|cut -f1 -d/`
echo "INFO: Pushing image $DOCKER_PUSH_IMAGE to $DOCKER_REGISTRY_URL"
docker push $DOCKER_PUSH_IMAGE || echo "Push failed. When running in " \
  "the CI, login should happen automatically. If running this script " \
  "by hand, you may need to run 'docker login' first and then re-run " \
  "this script."
