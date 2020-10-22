#!/usr/bin/env bash

set -exuo pipefail 

POLL_FREQ=1

LOCUST_LOCUSTFILE="../locust.py"
LOCUST_PRINT_STATS=1

# 1. Loop until we get a signal that the application is ready.

function appIsReady() {

    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \"session_token\": \""$SESSION_TOKEN"\"}" $ORCH_URL/api/poll|jq

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


waitForApp
buildArgs
startLoad
