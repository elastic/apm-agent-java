#!/usr/bin/env bash
set -eo pipefail

echo "--- Build opentelemetry-java-instrumentation"
git clone https://github.com/open-telemetry/opentelemetry-java-instrumentation.git
cd opentelemetry-java-instrumentation/
./gradlew assemble

echo "--- Download the latest elastic-agent.zip"
cd benchmark-overhead
ELASTIC_SNAPSHOT_URL=$(curl -s -u $GITHUB_USERNAME:$GITHUB_SECRET "https://api.github.com/repos/elastic/apm-agent-java/actions/workflows/49838992/runs?branch=main" | jq -c '.workflow_runs[] | {conclusion, updated_at, display_title, url}' | grep -v null  | grep -v pending | grep -v cancelled | grep success | head -1 | awk -F'":"' '{print $5}' | tr -d '"}')
ELASTIC_SNAPSHOT_ARTIFACTS=$(curl -s -u $GITHUB_USERNAME:$GITHUB_SECRET "$ELASTIC_SNAPSHOT_URL" | grep artifacts_url | awk -F'":' '{print $2}' | tr -d '"} ,')
ELASTIC_SNAPSHOT_ZIPFILE=$(curl -s -u $GITHUB_USERNAME:$GITHUB_SECRET "$ELASTIC_SNAPSHOT_ARTIFACTS" | jq -c ".artifacts[] | {name,archive_download_url}" | grep '"elastic-apm-agent"' | awk -F'":' '{print $3}' | tr -d '"}')
curl -s --output "elastic-agent.zip" -L -H "Accept: application/vnd.github+json" -H "Authorization: Bearer $GITHUB_SECRET" -H "X-GitHub-Api-Version: 2022-11-28" -u $GITHUB_USERNAME:$GITHUB_SECRET "$ELASTIC_SNAPSHOT_ZIPFILE"
unzip elastic-agent.zip
ELASTIC_SNAPSHOT_JAR=$(ls -1 elastic-apm-agent-*.jar)
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
