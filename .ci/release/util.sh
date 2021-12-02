#!/usr/bin/env bash

check_version() {
  v=${1:-}

  if [[ "${v}" == "" ]]; then
    echo "usage $0 <version>" # here $0 will be the calling script
    echo "where <version> in format '1.2.3'"
    exit 1
  fi

  if [[ ! "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "invalid version format '${v}'"
    exit 1
  fi
}

version_tag() {
    echo "v${1:?usage version_tag <version>}"
}

version_major_branch() {
  v="${1:?usage version_major_branch <version>}"
  echo "${v%%.*}.x"
}
