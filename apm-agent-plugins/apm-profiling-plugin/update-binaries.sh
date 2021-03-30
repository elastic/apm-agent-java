#!/usr/bin/env bash

set -euo pipefail

version="$1"
folder="$(dirname "$0")"
archive_prefix="async-profiler-${version}"

temp_folder=$(mktemp -d)

echo "${temp_folder}"
cd "${temp_folder}"

for url in $( \
    curl --silent "https://api.github.com/repos/jvm-profiling-tools/async-profiler/releases" | \
      jq -rM ".[] | select(.tag_name==\"v${version}\") | .assets[].browser_download_url" | \
      grep -v musl | grep "${archive_prefix}"); do

    set +e
    curl -O --location --silent ${url}
    set -e
done

cd -

for a in $(find ${temp_folder} -name '*.tar.gz'); do
    arch=$(basename ${a})
    arch=${arch%.tar.gz}
    arch=${arch#${archive_prefix}-}
    echo ${arch}
    tar xzf ${a} -C ${temp_folder}
    rm ${a}

    subfolder=${archive_prefix}-${arch}
    cp -v \
      ${temp_folder}/${subfolder}/build/libasyncProfiler.so \
      ${folder}/src/main/resources/asyncprofiler/libasyncProfiler-${arch}.so
done

rm -rf "${temp_folder}"
