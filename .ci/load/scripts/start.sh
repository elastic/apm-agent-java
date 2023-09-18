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

set -euo pipefail

response=$(curl -sS -X POST -H "Content-Type: application/json" -d \
"{\"app_token\": \"${APP_TOKEN}\", \
\"service\": \"application\", \
\"hostname\": \"test_app\", \
\"port\": \"999\"}" \
"${ORCH_URL}/api/register")
session_token=$("${response}" | jq -Mr '.session_created.session')
if [[ -z "${BUILDKITE}" ]]; then
  buildkite-agent env set "SESSION_TOKEN=${session_token}"
fi
echo "${session_token}"

