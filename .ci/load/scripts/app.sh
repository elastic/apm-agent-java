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
    curl -Is http://localhost:$APP_PORT/| head -1|egrep 200
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
    ps -ef|egrep 'app\.[wj]ar'|egrep java|awk '{print $2}'|xargs kill
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
  stopAppForce)
    lsof -i :${APP_PORT} | grep LISTEN | awk '{print $2}' | xargs kill -9
    exit 0
    ;;
esac

if [ ! $DEBUG_MODE ]; then
    trap "tearDown" ERR EXIT
    setUp
fi

waitForApp
sendAppReady
waitForLoad
waitForLoadFinish
stopApp
