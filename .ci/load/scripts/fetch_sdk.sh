#!/usr/bin/env bash

# Licensed to Elasticsearch B.V. under one or more contributor
# license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright
# ownership. Elasticsearch B.V. licenses this file to you under
# the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# ================================================================

# Call this script with an SDK to grab. The initial list should
# be populated in the Jenkinsfile by executing the script in the
# `param_gen` directory.

# This script will download and unpack whatever SDK is requested
# and then return to the caller the complete name of the directory
# which should be available to set as JAVA_HOME.

# Example execution for retreiving the SDK:

# ./fetch_sdk.sh oracle-13+33-linux
#
# This script requires the following tooling to be available on the
# system path prior to execution:
#
# 1. cURL   [https://curl.haxx.se/] 
# 2. jq     [https://stedolan.github.io/jq/]
# 3. tar    [https://www.gnu.org/software/tar/]
# ================================================================

#set -euo pipefail

CATALOG_URL="https://jvm-catalog.elastic.co/jdks/tags/linux,x86_64"
JDK_ID="${1}"

JDK_FOLDER="/tmp/${JDK_ID}"

JDK_JSON="${JDK_FOLDER}/jdk.json"
mkdir -p "${JDK_FOLDER}"

curl -s "${CATALOG_URL}" | jq ".[] | select(.id==\"${JDK_ID}\")" > "${JDK_JSON}"
if [ ! -s "${JDK_JSON}" ]
then
  echo "unknown JDK ID = ${JDK_ID}"
  exit 1
fi

read -d'\n' -r SDK_URL SDK_FILENAME <<<$(jq -Mr '.url, .filename' < "${JDK_JSON}")

JDK_ARCHIVE="${JDK_FOLDER}/${SDK_FILENAME}"

# we can't compute a hash on JSON because the URL is dynamically generated
# which makes the hash change at each request
if [[ ! -f "${JDK_ARCHIVE}" ]]
then
  curl -s -o "${JDK_ARCHIVE}" "${SDK_URL}"
  case "${JDK_ARCHIVE}" in
    *.zip)
      unzip -qq "${JDK_ARCHIVE}" -d "${JDK_FOLDER}"
      ;;
    *.tar.gz)
      tar xfz "${JDK_ARCHIVE}" -C "${JDK_FOLDER}"
      ;;
    *.bin)
      # assume IBM executable JVM installer
      INSTALLER_PROPERTIES=${JDK_FOLDER}/response.properties
      echo "INSTALLER_UI=silent" > ${INSTALLER_PROPERTIES}
      echo "USER_INSTALL_DIR=${JDK_FOLDER}/${JDK_ID}" >> ${INSTALLER_PROPERTIES}
      echo "LICENSE_ACCEPTED=TRUE" >> ${INSTALLER_PROPERTIES}
      mkdir -p "${JDK_FOLDER}/${JDK_ID}"
      chmod +x ${JDK_ARCHIVE}
      ${JDK_ARCHIVE} -i silent -f ${INSTALLER_PROPERTIES}
      ;;
  esac

fi


# JDK is stored within a sub-folder
SUB_FOLDER="$(find "${JDK_FOLDER}" -maxdepth 1 -mindepth 1 -type d)"
if [[ ! -d "${SUB_FOLDER}" ]]
then
  echo "JDK sub-folder not found in ${JDK_FOLDER}, cleaning up"
  rm -rf "${JDK_FOLDER}"
  exit 2
fi

echo "${SUB_FOLDER}"
