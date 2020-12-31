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

# polling frequency in seconds
POLL_FREQ=30
# application start polling frequency and max retries
APP_START_POLL_FREQ=5
APP_START_MAX_RETRIES=4

APP_PORT=8080

function setUp() {
    echo "Setting CPU frequency to base frequency"

    CPU_MODEL=$(lscpu | grep "Model name" | awk '{for(i=3;i<=NF;i++){printf "%s ", $i}; printf "\n"}')
    if [ "${CPU_MODEL}" == "Intel(R) Xeon(R) CPU E3-1246 v3 @ 3.50GHz " ]
    then
        # could also use `nproc`
        CORE_INDEX=7
        BASE_FREQ="3.5GHz"
    elif [ "${CPU_MODEL}" == "Intel(R) Core(TM) i7-6700 CPU @ 3.40GHz " ]
    then
        CORE_INDEX=7
        BASE_FREQ="3.4GHz"
    elif [ "${CPU_MODEL}" == "Intel(R) Core(TM) i7-7700 CPU @ 3.60GHz " ]
    then
        CORE_INDEX=7
        BASE_FREQ="3.6GHz"
    elif [ "${CPU_MODEL}" == "Intel(R) Core(TM) i7-8665U CPU @ 1.90GHz " ]
    then
        CORE_INDEX=7
        BASE_FREQ="1.9GHz"
    else
        >&2 echo "Cannot determine base frequency for CPU model [${CPU_MODEL}]. Please adjust the build script."
        exit 1
    fi
    MIN_FREQ=$(cpufreq-info -l -c 0 | awk '{print $1}')
    # This is the frequency including Turbo Boost. See also http://ark.intel.com/products/80916/Intel-Xeon-Processor-E3-1246-v3-8M-Cache-3_50-GHz
    MAX_FREQ=$(cpufreq-info -l -c 0 | awk '{print $2}')

    # set all CPUs to the base frequency
    for (( cpu=0; cpu<=${CORE_INDEX}; cpu++ ))
    do
        sudo -n cpufreq-set -c ${cpu} --min ${BASE_FREQ} --max ${BASE_FREQ}
    done

    # Build cgroups to isolate microbenchmarks and JVM threads
    echo "Creating groups for OS and microbenchmarks"
    # Isolate the OS to the first core
    sudo -n cset set --set=/os --cpu=0-1
    sudo -n cset proc --move --fromset=/ --toset=/os

    # Isolate the microbenchmarks to all cores except the first two (first physical core)
    # On a 4 core CPU with hyper threading, this would be 6 cores (3 physical cores)
    sudo -n cset set --set=/benchmark --cpu=2-${CORE_INDEX}
}

function appIsReady() {
    # Poll the app until it is ready
    curl -Is http://localhost:$APP_PORT/| head -1|grep 200
}

function sendAppReady() {
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"application\", \
    \"hostname\": \""$(hostname -I|cut -f1 -d ' ')"\", \
    \"port\": \"8080\"}" \
    $ORCH_URL/api/ready
}

function getAppPids() {
  ps -ef|grep -e 'app\.[wj]ar'|grep -e java|awk '{print $2}'
}

function waitForApp() {
    # Wait for the app to start
    left=${APP_START_MAX_RETRIES}
    while :
    do
        if appIsReady; then 
            break 
        fi
        if [[ 0 == "${left}" ]]; then
          echo "application did not start properly"
          exit 1
        fi
        sleep ${APP_START_POLL_FREQ};
        left=$((left-1))
    done
}

function waitForLoadGenState() {
    # Wait for the load generation to finish before we kill the app
    while :
    do
        if [ '' = "$(getAppPids)" ]; then
          echo "App JVM is not running anymore, stop polling"
          break;
        fi
        if checkLoadGenState "${1:-ready}"; then
            break 
        fi
        sleep $POLL_FREQ;
    done
}

function checkLoadGenState(){
    expected_state=${1:-ready}
    # Check to see if the load generation piece is sending requests
    curl -s -X POST -H "Content-Type: application/json" -d \
    "{\"app_token\": \""$APP_TOKEN"\", \
    \"session_token\": \""$SESSION_TOKEN"\", \
    \"service\": \"load_generation\", \
    \"hostname\": \"test_app\", \
    \"port\": \"8080\"}" \
    $ORCH_URL/api/poll | jq '.services.load_generation.state' | grep "${expected_state}"
}


function stopApp() {
    for pid in $(getAppPids); do
      kill -9 "${pid}"
    done
}

function tearDown() {
    echo "Destroying cgroups"
    sudo -n cset set --destroy /os
    sudo -n cset set --destroy /benchmark

    echo "Setting normal frequency range"
    for (( cpu=0; cpu<=${CORE_INDEX}; cpu++ ))
    do
        sudo -n cpufreq-set -c ${cpu} --min ${MIN_FREQ} --max ${MAX_FREQ}
    done
}

case "${1:-}" in
  stopApp)
    stopApp
    exit 0
    ;;
esac

if [ ! $DEBUG_MODE ]; then
    trap "tearDown" ERR EXIT
    setUp
fi

waitForApp
sendAppReady
waitForLoadGenState 'ready'
waitForLoadGenState 'stopped'

if [ '' = "$(getAppPids)" ]; then
    echo "abnormal application termination detected, stop load injection"

    # application crashed or stopped before we intentionally stopped it
    # (try to) end the load injection
    curl -s -X POST -H "Content-Type: application/json" -d \
        "{\"app_token\": \""$APP_TOKEN"\", \
        \"session_token\": \""$SESSION_TOKEN"\", \
        \"service\": \"load_generation\", \
        \"hostname\": \"test_app\", \
        \"port\": \"8080\"}" \
        $ORCH_URL/api/stop

    # will mark the build step as failed
    exit 2
else
    stopApp
fi
