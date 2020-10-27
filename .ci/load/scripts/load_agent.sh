#!/usr/bin/env bash

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
    docker run -e "LOCUST_HOST=$LOCUST_HOST" -e "LOCUST_RUN_TIME=$LOCUST_RUN_TIME" -p 8089:8089 -v ${PWD}/.ci/load/scripts:/locust locustio/locust -f /locust/locustfile.py -u 10 --headless
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
