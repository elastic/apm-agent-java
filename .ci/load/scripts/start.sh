#!/usr/bin/env bash

# set -exuo pipefail 

function registerSession() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"service\": \"application\", \
    \"hostname\": \"test_app\", \
    \"port\": \"999\"}" \
    $ORCH_URL/api/register | jq -Mr '.session_created.session'
}

registerSession