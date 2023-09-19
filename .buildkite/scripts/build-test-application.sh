#!/usr/bin/env bash

set -euo pipefail

current_working_dir="$(pwd)"
app_branch="main"
binary_ext="jar"

build_jdk_path=$(.ci/load/scripts/fetch_sdk.sh "${JVM_VERSION}")
major_jdk_version=$(jq -r '.version' "/tmp/${JVM_VERSION}/jdk.json")

if [[ ${major_jdk_version} = 7* ]]; then
  echo "Java 7.x detected. Installing compliant version of test application and JDK"
  app_branch="3450c3d99ecaaf46231feb2c404b72d1727517e1"
  build_jdk_path=$(.ci/load/scripts/fetch_jdk.sh zulu-8.0.272-linux)
  binary_ext="war"
fi

git clone --depth 1 --branch "${app_branch}" "https://github.com/spring-projects/${APP}" "${APP_BASE_DIR}"
cd "${APP_BASE_DIR}"

echo JAVA_HOME="${build_jdk_path}" java -version

JAVA_HOME="${build_jdk_path}" ./mvnw --batch-mode clean package -DskipTests=true -Dcheckstyle.skip
cp -v target/*."${binary_ext}" "${current_working_dir}/app.${binary_ext}"

cd -

buildkite-agent artifact upload "app.${binary_ext}"
