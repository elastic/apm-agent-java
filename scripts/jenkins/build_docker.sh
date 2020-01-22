#!/bin/bash
set -euxo pipefail

if ! which docker
then
  echo "ERROR: Building Docker image requires Docker binary to be installed" && exit 1
elif ! docker version
then
  echo "ERROR: Building Docker image requires Docker daemon to be running" && exit 1
elif ! which curl
then
  echo "ERROR: Building Docker image requires cURL to be installed" && exit 1
fi

echo "INFO: Fetching latest tag"
#TODO Move this into apm-pipeline-library
readonly GIT_TAG=`git describe --abbrev=0|sed s/^v//`

echo "INFO: Downloading artifact from Sonatype Nexus repository for version $GIT_TAG"
curl -L  -o apm-agent-java.jar "http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=co.elastic.apm&a=elastic-apm-agent&v=$GIT_TAG"

echo "INFO: Starting Docker build for version $GIT_TAG"

docker build . -t docker.elastic.co/apm/apm-agent-java:$GIT_TAG \
  --build-arg JAR_FILE=apm-agent-java.jar

if [ $? -eq 0 ]
then
  echo "INFO: Docker image built succesfully"
else
  echo "ERROR: Problem building Docker image!"
fi

function finish {
  echo "INFO: Cleaning up downloaded artifact"
  rm apm-agent-java.jar
}

trap finish EXIT
