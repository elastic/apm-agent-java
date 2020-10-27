#!/usr/bin/env bash

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
