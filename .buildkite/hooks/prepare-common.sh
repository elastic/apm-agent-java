#!/usr/bin/env bash
set -euo pipefail

# Configure the java version
JAVA_VERSION=$(cat .java-version)
JAVA_HOME="${HOME}/.java/openjdk${JAVA_VERSION}"
export JAVA_HOME
PATH="${JAVA_HOME}/bin:$PATH"
export PATH

# Fallback to install at runtime
if [ ! -d "${JAVA_HOME}" ] ; then
  # This should not be the case normally untless the .java-version file has been changed
  # and the VM Image is not yet available with the latest version.
  echo "--- Install JDK${JAVA_VERSION} :java:"
  JAVA_URL=https://jvm-catalog.elastic.co/jdk
  JAVA_PKG="${JAVA_URL}/latest_openjdk_${JAVA_VERSION}_linux.tar.gz"
  curl -L --output /tmp/jdk.tar.gz "$JAVA_PKG"
  mkdir -p "$JAVA_HOME"
  tar --extract --file /tmp/jdk.tar.gz --directory "$JAVA_HOME" --strip-components 1
fi

java -version
