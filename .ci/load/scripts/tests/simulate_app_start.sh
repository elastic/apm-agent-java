#!/usr/bin/env bash

set -exuo pipefail 

POLL_FREQ=1

function startApp() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \"session_token\": \""$SESSION_TOKEN"\", \"service\": \"application\", \"hostname\": \"test_app\", \"port\": \"999\"}" $ORCH_URL/api/ready 
}

startApp