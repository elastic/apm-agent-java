#!/usr/bin/env bash

set -euxo pipefail
MOD="integration-tests" 
for i in $(find ${MOD} -maxdepth 1 -mindepth 1 -type d|grep -v target)
do 
  MOD="${MOD},${i}"
done
./mvnw -Dmaven.javadoc.skip=true -pl ${MOD} -am compile verify