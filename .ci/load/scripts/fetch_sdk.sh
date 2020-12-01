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

# set -exuo pipefail 


CATALOG_URL="https://jvm-catalog.elastic.co/jdks"

read -d'\n' -r SDK_URL SDK_FILENAME <<<$(curl -s $CATALOG_URL|jq -Mr '.['\"$1\"'].url, .['\"$1\"'].filename')

curl -s -o /tmp/${SDK_FILENAME} ${SDK_URL}

JDK_FOLDER=$(mktemp -d)

tar xfz /tmp/${SDK_FILENAME} -C ${JDK_FOLDER}

SUB_FOLDER=$(ls ${JDK_FOLDER})
echo $(readlink -f ${JDK_FOLDER}/${SUB_FOLDER})
