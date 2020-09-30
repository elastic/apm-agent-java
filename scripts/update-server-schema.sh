#!/bin/bash

set -eou pipefail

repo=apm-server
ref=${1:-master}
url=https://codeload.github.com/elastic/${repo}/tar.gz/${ref}
target_folder="${2:-./apm-server-schema/${ref}}"

rm -rf ${target_folder} 
mkdir -p ${target_folder} 

curl --silent --fail ${url} | \
tar xzvf - \
--directory ${target_folder} \
--wildcards \
--strip-components=3 \
"apm-server-${ref#v}/docs/spec/*"

