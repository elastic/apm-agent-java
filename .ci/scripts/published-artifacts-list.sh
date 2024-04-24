#!/usr/bin/env bash

targets="$(find . -type d -name 'target'|grep -v apm-agent-plugins|grep -v integration-tests|sort)"

for t in ${targets}; do
    find "${t}" \
        -name '*.jar' \
        | grep -v '\-sources.jar' \
        | grep -v '\-tests.jar' \
        | grep -v '\-javadoc.jar' \
        | grep -v 'original-' \
        | grep -v 'classes/' \
        | grep -v 'benchmarks' \
        | grep -v 'apm-agent-bootstrap' \
        | grep -v 'apm-agent-builds' \
        | grep -v 'apm-agent-cached-lookup-key' \
        | grep -v 'apm-agent-core' \
        | grep -v 'apm-agent-common' \
        | grep -v 'apm-agent-lambda-layer' \
        | grep -v 'apm-agent-premain'
done
