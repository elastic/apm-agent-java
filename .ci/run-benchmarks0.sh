#!/usr/bin/env bash

set -e

NOW_ISO_8601=$(date -u "+%Y-%m-%dT%H%M%SZ")

echo $(pwd)

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
        sudo cpufreq-set -c ${cpu} --min ${BASE_FREQ} --max ${BASE_FREQ}
    done

    # Build cgroups to isolate microbenchmarks and JVM threads
    echo "Creating groups for OS and microbenchmarks"
    # Isolate the OS to the first core
    sudo cset set --set=/os --cpu=0-1
    sudo cset proc --move --fromset=/ --toset=/os

    # Isolate the microbenchmarks to all cores except the first two (first physical core)
    # On a 4 core CPU with hyper threading, this would be 6 cores (3 physical cores)
    sudo cset set --set=/benchmark --cpu=2-${CORE_INDEX}
}

function benchmark() {
    COMMIT_ISO_8601=$(git log -1 -s --format=%cI)
    COMMIT_UNIX=$(git log -1 -s --format=%ct)

    ./mvnw clean package -DskipTests=true

    RESULT_FILE=apm-agent-benchmark-results-${COMMIT_ISO_8601}.json
    BULK_UPLOAD_FILE=apm-agent-bulk-${NOW_ISO_8601}.json

    sudo cset proc --exec /benchmark -- \
        $JAVA_HOME/bin/java -jar apm-agent-benchmarks/target/benchmarks.jar ".*ContinuousBenchmark" \
        -prof gc \
        -prof co.elastic.apm.benchmark.profiler.ReporterProfiler \
        -rf json \
        -rff ${RESULT_FILE}

    # remove strange non unicode chars inserted by JMH; see org.openjdk.jmh.results.Defaults.PREFIX
    tr -cd '\11\12\40-\176' < ${RESULT_FILE} > "${RESULT_FILE}.clean"
    rm -f ${RESULT_FILE} ${BULK_UPLOAD_FILE}
    mv "${RESULT_FILE}.clean" ${RESULT_FILE}

    $JAVA_HOME/bin/java -cp apm-agent-benchmarks/target/benchmarks.jar co.elastic.apm.benchmark.PostProcessBenchmarkResults ${RESULT_FILE} ${BULK_UPLOAD_FILE} ${COMMIT_UNIX}
}

function tearDown() {
    echo "Destroying cgroups"
    sudo cset set --destroy /os
    sudo cset set --destroy /benchmark

    echo "Setting normal frequency range"
    for (( cpu=0; cpu<=${CORE_INDEX}; cpu++ ))
    do
        sudo cpufreq-set -c ${cpu} --min ${MIN_FREQ} --max ${MAX_FREQ}
    done
}

trap "tearDown" EXIT

setUp
benchmark
