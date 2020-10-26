#!/usr/bin/env bash

set -exuo pipefail 

POLL_FREQ=1

APP_PORT=999


function sendAppReady() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"load_generation\", \
    \"hostname\": \""$(hostname)"\", \
    \"port\": \"999\"}" \
    $ORCH_URL/api/ready 

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
    export LOCUST_HOST=$(curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \"session_token\": \""$SESSION_TOKEN"\"}" $ORCH_URL/api/poll | jq '.services.application.'})

    export LOCUST_RUN_TIME=30
}

function startLoad() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \"session_token\": \""$SESSION_TOKEN"\", \"service\": \"load_generation\"}" $ORCH_URL/api/ready

    docker run -p 8089:8089 -v ${PWD}/.ci/load/scripts:/locust locustio/locust -f /locust/locustfile.py
}

function stopLoad() {
    # Stop the load test
    docker ps|egrep locust|awk '{print $1}'|xargs docker kill
}

sendAppReady
# waitForApp
# buildArgs
# startLoad