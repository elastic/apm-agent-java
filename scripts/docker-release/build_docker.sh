#!/usr/bin/env bash

# See full documentation in the "Creating and publishing Docker images" section
# of CONTRIBUTING.md

set -euxo pipefail

if ! command -v docker
then
  echo "ERROR: Building Docker image requires Docker binary to be installed" && exit 1
elif ! docker version
then
  echo "ERROR: Building Docker image requires Docker daemon to be running" && exit 1
fi
readonly RELEASE_VERSION=${1}

readonly SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
readonly PROJECT_ROOT=$SCRIPT_PATH/../../
readonly NAMESPACE="observability"

set +e
FILE=$(ls -A ${PROJECT_ROOT}elastic-apm-agent/target/*.jar | grep -E "elastic-apm-agent-[0-9]+.[0-9]+.[0-9]+(-SNAPSHOT)?.jar" )
set -e

if [ -n "${FILE}" ]
then
  # We have build files to use
  echo "INFO: Found local build artifact. Using locally built for Docker build"
  cp "${FILE}" "${PROJECT_ROOT}apm-agent-java.jar" || echo "INFO: No locally built image found"
elif [ ! -z ${SONATYPE_FALLBACK+x} ]
then
  echo "INFO: No local build artifact and SONATYPE_FALLBACK. Falling back to downloading artifact from Sonatype Nexus repository for version $RELEASE_VERSION"
  if ! command -v curl
  then
      echo "ERROR: Pulling images from Sonatype Nexus repo requires cURL to be installed" && exit 1
  fi
  curl -L -s --fail-with-body -o apm-agent-java.jar \
    "https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/$RELEASE_VERSION/elastic-apm-agent-$RELEASE_VERSION.jar"
  else
    echo "ERROR: No suitable build artifact was found. Re-running this script with the SONATYPE_FALLBACK variable set to true will try to use the Sonatype artifact for the latest tag"
    exit 1
fi

ls -l apm-agent-java.jar

echo "INFO: Starting Docker build for version $RELEASE_VERSION"
for DOCKERFILE in "Dockerfile" "Dockerfile.wolfi" ; do
  DOCKER_TAG=$RELEASE_VERSION
  if [[ $DOCKERFILE =~ "wolfi" ]]; then
    DOCKER_TAG="${RELEASE_VERSION}-wolfi"
  fi
  docker build -t docker.elastic.co/$NAMESPACE/apm-agent-java:$DOCKER_TAG \
    --platform linux/amd64 \
    --build-arg JAR_FILE=apm-agent-java.jar \
    --build-arg HANDLER_FILE=apm-agent-lambda-layer/src/main/assembly/elastic-apm-handler \
    --file $DOCKERFILE .

  if [ $? -eq 0 ]
  then
    echo "INFO: Docker image built successfully"
  else
    echo "ERROR: Problem building Docker image!"
  fi
done

function finish {

  if [ -f apm-agent-java.jar ]
  then
    echo "INFO: Cleaning up downloaded artifact"
    rm apm-agent-java.jar
  fi
}

trap finish EXIT
