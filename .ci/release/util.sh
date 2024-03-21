#!/usr/bin/env bash

check_version() {
  v=${1:-}

  if [ -z "${v}" ]; then
    >&2 echo "The environment variable 'RELEASE_VERSION' isn't defined"
    exit 1
  fi
  if [[ ! "${v}" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    >&2 echo "The environment variable 'RELEASE_VERSION' should respect SemVer format"
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
