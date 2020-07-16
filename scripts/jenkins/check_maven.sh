#!/usr/bin/env bash
set -eo pipefail

# Use: check_maven.sh --url https://status.maven.org/api/v2/summary.json --component OSSRH
#
# This script checks a status page to determine if a service is down. If the requested service
# is up, no output is returned to standard out and a return code of 0 is set. If a service is down
# then a message which be printed to standard out and a return code of 1 is set.

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

JQ=$(which jq)
if [ -z $JQ ];
then
    echo "Must have jq installed"
    exit 1
fi

CURL=$(which curl)
if [ -z $CURL ];
then
    echo "Must have curl installed"
    exit 1
fi

case $key in
    -f|--component)
    FILTER="$2"
    shift
    shift
    ;;
    -u|--url)
    URL="$2"
    shift
    shift
    ;;
    -h|--help)
    echo "Use: check_maven.sh --url https://status.maven.org/api/v2/summary.json --component OSSRH"
    exit 0
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done

NON_OP=$($CURL -s $URL | $JQ '.components|map(select(."status"!="operational"))|map(.name)')

if [[ $NON_OP == "[]" ]];
then
exit 0
else
    if [ ${FILTER+x} ];
    then
        FILT_RET=$(echo $NON_OP | $JQ "map(select(.==\"${COMPONENT}\"))" | tr -d "\n")
        echo "The following services are down: $FILT_RET. Check failed."
    else
        RET=$(echo $NON_OP |tr -d "\n")
        echo "The following services are down: $RET. Check failed"
    exit 1
    fi
fi
