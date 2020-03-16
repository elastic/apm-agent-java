#!/bin/bash

set -euo pipefail

tar_executable=tar
if [ -x "$(command -v gtar)" ]; then
  tar_executable=gtar
fi

download_gherkin()
{
  folder="${1:-/tmp/gherkin_specs}"
  branch="${2:-master}"
    rm -rf "${folder}" && mkdir -p "${folder}"
    for run in 1 2 3 4 5; do
      curl --silent --fail https://codeload.github.com/elastic/apm/tar.gz/${branch} | \
        $tar_executable xzvf - --wildcards --directory=${1} --strip-components=4 "*/tests/agents/gherkin-specs*"
      result=$?
      if [ $result -eq 0 ]; then break; fi
      echo "download fail ${run}/5"
      sleep 1
    done

    if [ $result -ne 0 ]; then
      echo "download fail"
      exit $result;
    fi

    echo "download success to folder ${folder}"
}

# parent directory
basedir=$(dirname "$0")

download_gherkin "${1:-${basedir}/specs}" "${2:-master}"
