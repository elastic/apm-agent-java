#!/usr/bin/env bash

# Bash strict mode
set -eo pipefail

# Found current script directory
RELATIVE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Found project directory
BASE_PROJECT="$(dirname "$(dirname "${RELATIVE_DIR}")")"

## Buildkite specific configuration
if [ "${CI}" == "true" ] ; then
  # If HOME is not set then use the Buildkite workspace
  # that's normally happening when running in the CI
  # owned by Elastic.
  if [ -z "${HOME}" ] ; then
    export HOME="${BUILDKITE_BUILD_CHECKOUT_PATH}"
    export HOME
  fi

  # required when running the benchmark
  PATH="${PATH}:${HOME}/.local/bin"
  export PATH

  echo 'Docker login is done in the Buildkite hooks'
fi

# Validate env vars
[ -z "${ES_USER_SECRET}" ] && echo "Environment variable 'ES_USER_SECRET' must be defined" && exit 1;
[ -z "${ES_PASS_SECRET}" ] && echo "Environment variable 'ES_PASS_SECRET' must be defined" && exit 1;
[ -z "${ES_URL_SECRET}" ] && echo "Environment variable 'ES_URL_SECRET' must be defined" && exit 1;
[ -z "${JAVA_VERSION}" ] && echo "Environment variable 'JAVA_VERSION' must be defined" && exit 1;

# Debug env vars
echo "Will run microbenchmarks with JAVA_VERSION=${JAVA_VERSION}"

# Fetch sdk
export JAVA_HOME=$("${BASE_PROJECT}/.ci/load/scripts/fetch_sdk.sh" "${JAVA_VERSION}")
export PATH="${JAVA_HOME}/bin:${PATH}"

# Run benchmark
NOW_ISO_8601=$(date -u "+%Y-%m-%dT%H%M%SZ")
NO_BUILD="" NOW_ISO_8601="${NOW_ISO_8601}" "${BASE_PROJECT}/.ci/run-benchmarks.sh"

# Then we ship the data using the helper
sendBenchmark "${ES_USER_SECRET}" "${ES_PASS_SECRET}" "${ES_URL_SECRET}" "${BASE_PROJECT}/apm-agent-bulk-${NOW_ISO_8601}.json"
