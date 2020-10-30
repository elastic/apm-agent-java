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

set -exuo pipefail 

POLL_FREQ=1

LOCUST_LOCUSTFILE="../locust.py"
LOCUST_PRINT_STATS=1

# 1. Loop until we get a signal that the application is ready.

function appIsReady() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \"session_token\": \""$SESSION_TOKEN"\"}" $ORCH_URL/api/poll | \
    jq -e '.services.application.state == "ready"'
}

function waitForApp() {
    while :
    do
        if appIsReady; then 
            break 
        fi
        sleep $POLL_FREQ;
    done
}

function buildArgs() {
    LOCUST_HOSTNAME="$(curl -s -X POST -H "Content-Type: application/json" -d \
        "{\"app_token\": \
        \""$APP_TOKEN"\", \
        \"session_token\": \""$SESSION_TOKEN"\"}" \
        $ORCH_URL/api/poll | \
        jq '.services.application.hostname')"
    
    LOCUST_PORT="$(curl -s -X POST -H "Content-Type: application/json" -d \
        "{\"app_token\": \
        \""$APP_TOKEN"\", \
        \"session_token\": \""$SESSION_TOKEN"\"}" \
        $ORCH_URL/api/poll | \
        jq '.services.application.port')"
    # FIXME temporary value
    export LOCUST_HOST=http://$(echo $LOCUST_HOSTNAME|sed 's/"//g'):$(echo $LOCUST_PORT|sed 's/"//g')
    export LOCUST_RUN_TIME=30s
}

function startLoad() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"load_generation\", \
    \"hostname\": \"test_app\", \
    \"port\": \"8080\"}" \
    $ORCH_URL/api/ready 
    # FIXME put number of concurrent users in
    docker run -e "LOCUST_HOST=$LOCUST_HOST" -e "LOCUST_RUN_TIME=$LOCUST_RUN_TIME" -e "LOCUST_USERS=$LOCUST_USERS" -p 8089:8089 -v ${PWD}/.ci/load/scripts:/locust locustio/locust -f /locust/locustfile.py -u 10 --headless
}

function stopLoad() {
    # This happens as soon as the container exits so there is nothing to kill
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"load_generation\", \
    \"hostname\": \"test_app\", \
    \"port\": \"8080\"}" \
    $ORCH_URL/api/stop 
}


waitForApp
buildArgs
startLoad
stopLoad
