#!/usr/bin/env bash
#
# Build agent
#
set -euo pipefail

agent_jar_path="$(pwd)/elastic-apm-agent.jar"
git clone "https://github.com/elastic/${REPO}.git" "${AGENT_BASE_DIR}"
cd "${AGENT_BASE_DIR}"
git checkout "origin/${APM_VERSION}"
./mvnw --batch-mode clean package -DskipTests=true -Dmaven.javadoc.skip=true -Dmaven.sources.skip=true
cp -v "$(find ./elastic-apm-agent/target -name '*.jar' | grep -v sources | grep -v original | grep -v javadoc)" "${agent_jar_path}"

if [[ -z ${BUILDKITE} ]]; then
  buildkite-agent artifact upload "${agent_jar_path}"
fi
