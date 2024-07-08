#!/usr/bin/env bash
set -eo pipefail

CONTAINER_NAME=mock-apm-server
JSON_FILE="$(pwd)/output.json"

function cleanup {
  echo "--- Tear down the environment"
  MOCK_APM_SERVER=$(docker ps | grep $CONTAINER_NAME | awk '{print $1}')
  docker stop $MOCK_APM_SERVER
  docker rm $MOCK_APM_SERVER
}

trap cleanup EXIT

echo "--- Download the latest elastic-apm-agent artifact"
# run earlier so gh can use the current github repository.
run_id=$(gh run list --branch main --status success --workflow main.yml -L 1 --json databaseId --jq '.[].databaseId')
echo "downloading the latest artifact 'elastic-apm-agent' (using the workflow run '$run_id')"
gh run download "$run_id" -n elastic-apm-agent
ELASTIC_SNAPSHOT_JAR=$(ls -1 elastic-apm-agent-*.jar)
ELASTIC_SNAPSHOT_JAR_FILE="$(pwd)/$ELASTIC_SNAPSHOT_JAR"
echo "$ELASTIC_SNAPSHOT_JAR_FILE has been downloaded."
gh run download "$run_id" -n apm-agent-benchmarks
BENCHMARKS_JAR=$(ls -1 benchmarks*.jar)
BENCHMARKS_JAR_FILE="$(pwd)/$BENCHMARKS_JAR"
echo "$BENCHMARKS_JAR_FILE has been downloaded."

echo "--- Download the latest elastic-otel-agent artifact"
distro_run_id=$(gh run list --repo elastic/elastic-otel-java --branch main --status success --workflow build.yml -L 1 --json databaseId --jq '.[].databaseId')
gh run download "$distro_run_id" --repo elastic/elastic-otel-java -n elastic-otel-javaagent
ELASTIC_OTEL_DISTRO_SNAPSHOT_JAR=$(ls -1 elastic-otel-javaagent-*SNAPSHOT.jar)
ELASTIC_OTEL_DISTRO_SNAPSHOT_JAR_FILE="$(pwd)/$ELASTIC_OTEL_DISTRO_SNAPSHOT_JAR"
echo "$ELASTIC_OTEL_DISTRO_SNAPSHOT_JAR_FILE has been downloaded."


echo "--- Start APM Server mock"
git clone https://github.com/elastic/apm-k8s-attacher.git
pushd apm-k8s-attacher/test/mock
docker build -t $CONTAINER_NAME .
docker run -dp 127.0.0.1:8027:8027 $CONTAINER_NAME
popd

echo "--- Build opentelemetry-java-instrumentation"
git clone https://github.com/open-telemetry/opentelemetry-java-instrumentation.git --depth 1 --branch main
pushd opentelemetry-java-instrumentation/
./gradlew assemble

echo "--- Customise the elastic opentelemetry java instrumentation"
pushd benchmark-overhead
cp "$ELASTIC_SNAPSHOT_JAR_FILE" .
cp "$ELASTIC_OTEL_DISTRO_SNAPSHOT_JAR_FILE" .
ELASTIC_LATEST_VERSION=$(curl -s https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/ | perl -ne 's/<.*?>//g; if(s/^([\d\.]+).*$/$1/){print}' | sort -V | tail -1)
ELASTIC_OTEL_DISTRO_LATEST_VERSION=$(curl -s https://repo1.maven.org/maven2/co/elastic/otel/elastic-otel-javaagent/ | perl -ne 's/<.*?>//g; if(s/^([\d\.]+).*$/$1/){print}' | sort -V | tail -1)
ELASTIC_SNAPSHOT_ENTRY="new Agent(\\\"elastic-snapshot\\\",\\\"latest available snapshot version from elastic main\\\",\\\"file://$PWD/$ELASTIC_SNAPSHOT_JAR\\\", java.util.List.of(\\\"-Delastic.apm.server_url=http://host.docker.internal:8027/\\\"))"
ELASTIC_LATEST_ENTRY="new Agent(\\\"elastic-latest\\\",\\\"latest available released version from elastic main\\\",\\\"https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/$ELASTIC_LATEST_VERSION/elastic-apm-agent-$ELASTIC_LATEST_VERSION.jar\\\", java.util.List.of(\\\"-Delastic.apm.server_url=http://host.docker.internal:8027/\\\"))"
ELASTIC_LATEST_ENTRY2="new Agent(\\\"elastic-async\\\",\\\"latest available released version from elastic main\\\",\\\"https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/$ELASTIC_LATEST_VERSION/elastic-apm-agent-$ELASTIC_LATEST_VERSION.jar\\\", java.util.List.of(\\\"-Delastic.apm.delay_agent_premain_ms=15000\\\",\\\"-Delastic.apm.server_url=http://host.docker.internal:8027/\\\"))"
ELASTIC_OTEL_DISTRO_SNAPSHOT_ENTRY="new Agent(\\\"distro-snapshot\\\",\\\"latest available snapshot version from elastic-otel-java main\\\",\\\"file://$PWD/$ELASTIC_OTEL_DISTRO_SNAPSHOT_JAR\\\")"
ELASTIC_OTEL_DISTRO_LATEST_ENTRY="new Agent(\\\"distro-latest\\\",\\\"latest available released version from elastic-otel-java\\\",\\\"https://repo1.maven.org/maven2/co/elastic/otel/elastic-otel-javaagent/$ELASTIC_OTEL_DISTRO_LATEST_VERSION/elastic-otel-javaagent-$ELASTIC_OTEL_DISTRO_LATEST_VERSION.jar\\\")"
ELASTIC_OTEL_DISTRO_FEATURE_ENTRIES=""
for FEATURE in "infspan" "cloudres" "up-integ" "spanstack" ; do
  FEATURE_ARGS=""
  if [ "$FEATURE" = "infspan" ]; then
    FEATURE_ARGS="$FEATURE_ARGS, \\\"-Delastic.otel.inferred.spans.enabled=true\\\""
  fi
  if [ "$FEATURE" != "cloudres" ]; then
    FEATURE_ARGS="$FEATURE_ARGS, \\\"-Dotel.resource.providers.aws.enabled=false\\\", \\\"-Dotel.resource.providers.gcp.enabled=false\\\""
  fi

  if [ "$FEATURE" = "up-integ" ]; then
    FEATURE_ARGS="$FEATURE_ARGS, \\\"-Delastic.otel.universal.profiling.integration.enabled=true\\\""
  else
    FEATURE_ARGS="$FEATURE_ARGS, \\\"-Delastic.otel.universal.profiling.integration.enabled=false\\\""
  fi
  if [ "$FEATURE" != "spanstack" ]; then
    FEATURE_ARGS="$FEATURE_ARGS, \\\"-Delastic.otel.span.stack.trace.min.duration=-1\\\""
  fi
  # Remove leading ", "
  FEATURE_ARGS="${FEATURE_ARGS:2}"
  ELASTIC_OTEL_DISTRO_FEATURE_ENTRIES="$ELASTIC_OTEL_DISTRO_FEATURE_ENTRIES, new Agent(\\\"distro-$FEATURE\\\",\\\"latest available snapshot version from elastic-otel-java main with only feature $FEATURE enabled\\\",\\\"file://$PWD/$ELASTIC_OTEL_DISTRO_SNAPSHOT_JAR\\\", java.util.List.of($FEATURE_ARGS))"
done
NEW_LINE="              .withAgents(Agent.NONE, Agent.LATEST_RELEASE, Agent.LATEST_SNAPSHOT, $ELASTIC_LATEST_ENTRY, $ELASTIC_LATEST_ENTRY2, $ELASTIC_SNAPSHOT_ENTRY, $ELASTIC_OTEL_DISTRO_SNAPSHOT_ENTRY, $ELASTIC_OTEL_DISTRO_LATEST_ENTRY $ELASTIC_OTEL_DISTRO_FEATURE_ENTRIES)"
echo $NEW_LINE
perl -i -ne "if (/withAgents/) {print \"$NEW_LINE\n\"}else{print}" src/test/java/io/opentelemetry/config/Configs.java

echo "--- Run tests of benchmark-overhead"
./gradlew test

echo "--- Report in Buildkite"

REPORT_FILE=$(pwd)/build/reports/tests/test/classes/io.opentelemetry.OverheadTests.html
perl -ne '/Standard output/ && $on++; /\<\/pre\>/ && ($on=0);$on && s/\<.*\>//;$on && !/^\s*$/ && print' $REPORT_FILE | tee report.txt

# Buildkite annotation
if [ -n "$BUILDKITE" ]; then
  REPORT=$(cat report.txt)
  cat << EOF | buildkite-agent annotate --style "info" --context report
  ### OverheadTests Report

  \`\`\`
  ${REPORT}
  \`\`\`

EOF
fi

echo "--- Generate ES docs"
java -cp $BENCHMARKS_JAR_FILE \
  co.elastic.apm.agent.benchmark.ProcessOtelBenchmarkResults \
  "$REPORT_FILE" "$JSON_FILE" "$ELASTIC_LATEST_VERSION" ./opentelemetry-javaagent.jar

echo "--- Send Report"
curl -X POST \
  --user "${ES_USER_SECRET}:${ES_PASS_SECRET}" \
  "${ES_URL_SECRET}/_bulk?pretty" \
  -H "Content-Type: application/x-ndjson" \
  --data-binary @"$JSON_FILE"
