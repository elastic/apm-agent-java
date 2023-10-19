#!/usr/bin/env bash
set -eo pipefail

echo "--- Download the latest elastic-agent.zip"
# run earlier so gh can use the current github repository.
run_id=$(gh run list --branch main --status success --workflow main.yml -L 1 --json databaseId --jq '.[].databaseId')
gh run download "$run_id" -n elastic-apm-agent
ELASTIC_SNAPSHOT_JAR=$(ls -1 elastic-apm-agent-*.jar)
ELASTIC_SNAPSHOT_JAR_FILE="$(pwd)/$ELASTIC_SNAPSHOT_JAR"

echo "--- Build opentelemetry-java-instrumentation"
git clone https://github.com/open-telemetry/opentelemetry-java-instrumentation.git --depth 1 --branch main
cd opentelemetry-java-instrumentation/
./gradlew assemble

echo "--- Customise the elastic opentelemetry java instrumentation"
cd benchmark-overhead
cp "$ELASTIC_SNAPSHOT_JAR_FILE" .
ELASTIC_SNAPSHOT_ENTRY="new Agent(\\\"elastic-snapshot\\\",\\\"latest available snapshot version from elastic main\\\",\\\"file://$PWD/$ELASTIC_SNAPSHOT_JAR\\\")"
ELASTIC_LATEST_VERSION=$(curl -s https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/ | perl -ne 's/<.*?>//g; if(s/^([\d\.]+).*$/$1/){print}' | sort -V | tail -1)
ELASTIC_LATEST_ENTRY="new Agent(\\\"elastic-latest\\\",\\\"latest available released version from elastic main\\\",\\\"https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/$ELASTIC_LATEST_VERSION/elastic-apm-agent-$ELASTIC_LATEST_VERSION.jar\\\")"
ELASTIC_LATEST_ENTRY2="new Agent(\\\"elastic-async\\\",\\\"latest available released version from elastic main\\\",\\\"https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/$ELASTIC_LATEST_VERSION/elastic-apm-agent-$ELASTIC_LATEST_VERSION.jar\\\", java.util.List.of(\\\"-Delastic.apm.delay_agent_premain_ms=15000\\\"))"
NEW_LINE="              .withAgents(Agent.NONE, Agent.LATEST_RELEASE, Agent.LATEST_SNAPSHOT, $ELASTIC_LATEST_ENTRY, $ELASTIC_LATEST_ENTRY2, $ELASTIC_SNAPSHOT_ENTRY)"
echo $NEW_LINE
perl -i -ne "if (/withAgents/) {print \"$NEW_LINE\n\"}else{print}" src/test/java/io/opentelemetry/config/Configs.java

echo "--- Run tests with sudo access"
./gradlew test
perl -ne '/Standard output/ && $on++; /\<\/pre\>/ && ($on=0);$on && s/\<.*\>//;$on && !/^\s*$/ && print' build/reports/tests/test/classes/io.opentelemetry.OverheadTests.html
