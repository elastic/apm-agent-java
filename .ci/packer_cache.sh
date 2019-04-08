#!/usr/bin/env bash
JAVA_HOME=$HOME/.java/java10 ./mvnw clean verify

docker pull store/oracle/weblogic:12.2.1.3-dev
