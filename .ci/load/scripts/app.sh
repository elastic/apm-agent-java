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

APP_PORT=8080

function appIsReady() {
    # Poll the app until it is ready
    curl -Is http://localhost:$APP_PORT/| head -1|egrep 200
}

function sendAppReady() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"application\", \
    \"hostname\": \""$(hostname)"\", \
    \"port\": \"8080\"}" \
    $ORCH_URL/api/ready 
    
    
}

function waitForApp() {
    # Wait for the load generation to finish before we kill the app
    while :
    do
        if appIsReady; then 
            break 
        fi
        sleep $POLL_FREQ;
    done
}

function waitForLoad() {
    # Wait for the load generation to finish before we kill the app
    while :
    do
        if checkLoadGen; then 
            break 
        fi
        sleep $POLL_FREQ;
    done
}

function waitForLoadFinish() {
    # Wait for the load generation to finish before we kill the app
    while :
    do
        if checkLoadGenFinish; then 
            break 
        fi
        sleep $POLL_FREQ;
    done
}


function checkLoadGen(){
    # Check to see if the load generation piece is sending requests
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"load_generation\", \
    \"hostname\": \"test_app\", \
    \"port\": \"8080\"}" \
    $ORCH_URL/api/poll | jq '.services.load_generation.state' | egrep 'ready'
}


function checkLoadGenFinish(){
    # Check to see if the load generation piece is sending requests
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"load_generation\", \
    \"hostname\": \"test_app\", \
    \"port\": \"8080\"}" \
    $ORCH_URL/api/poll | jq '.services.load_generation.state' | egrep 'stopped'
}


function stopApp() {
    ps -ef|egrep spring-boot:run|egrep java|awk '{print $2}'|xargs kill
}

waitForApp
sendAppReady
waitForLoad
waitForLoadFinish
stopApp
