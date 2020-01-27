#!/usr/bin/env bash
set -euxo pipefail

if ! command -v docker
then
  echo "ERROR: Building Docker image requires Docker binary to be installed" && exit 1
elif ! docker version
then
  echo "ERROR: Building Docker image requires Docker daemon to be running" && exit 1
elif ! command -v curl
then
  echo "ERROR: Building Docker image requires cURL to be installed" && exit 1
fi

echo "INFO: Determining latest tag"
if [ ! -z ${TAG_NAME+x} ]
then
  echo "INFO: Detected TAG_NAME variable. Probably a Jenkins instance."
  readonly GIT_TAG_DEFAULT=$(echo $TAG_NAME|sed s/^v//)
else
  echo "INFO: Did not detect TAG_NAME. Examining git log for latest tag"
  readonly GIT_TAG_DEFAULT=$(git describe --abbrev=0|sed s/^v//)
fi

readonly GIT_TAG=${GIT_TAG:-$GIT_TAG_DEFAULT}

readonly SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
readonly PROJECT_ROOT=$SCRIPT_PATH/../../
readonly NAMESPACE="observability"

if [ "$(ls -A  ${PROJECT_ROOT}elastic-apm-agent/target/*.jar)" ]
then
  # We have build files to use
  echo "INFO: Found local build artifact. Using locally built for Docker build"
  find -E ${PROJECT_ROOT}elastic-apm-agent/target -regex '.*/elastic-apm-agent-[0-9]+.[0-9]+.[0-9]+(-SNAPSHOT)?.jar' -exec cp {} ${PROJECT_ROOT}apm-agent-java.jar \; || echo "INFO: No locally built image found"
elif [ ! -z ${SONATYPE_FALLBACK+x} ]
then
  echo "INFO: No local build artifact and SONATYPE_FALLBACK. Falling back to downloading artifact from Sonatype Nexus repository for version $GIT_TAG"
  curl -L -s -o apm-agent-java.jar \
    "http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=co.elastic.apm&a=elastic-apm-agent&v=$GIT_TAG"
  else
    echo "ERROR: No suitable build artifact was found. Re-running this script with the SONATYPE_FALLBACK variable set to true will try to use the Sonatype artifact for the latest tag"
    exit 1
fi

echo "INFO: Starting Docker build for version $GIT_TAG"

docker build -t docker.elastic.co/$NAMESPACE/apm-agent-java:$GIT_TAG \
  --build-arg JAR_FILE=apm-agent-java.jar .

if [ $? -eq 0 ]
then
  echo "INFO: Docker image built succesfully"
else
  echo "ERROR: Problem building Docker image!"
fi

function finish {

  if [ -f apm-agent-java.jar ]
  then
    echo "INFO: Cleaning up downloaded artifact"
    rm apm-agent-java.jar
  fi
}

trap finish EXIT
