#!/usr/bin/env bash

set -euo pipefail

REMOTE_NAME=origin

# Final state (when there is no error): local checkout in the major branch '1.x' that matches the release tag
# Pushing to remote repository is managed by the caller

v=${1:-}

if [[ "$v" == "" ]]; then
  echo "usage $0 <version>"
  echo "where <version> in format '1.2.3'"
  exit 1
fi

if [[ ! "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "invalid version '$v'"
  exit 1
fi

tag="v$v"
major_branch="${v%%.*}.x"

echo ""
echo "release version: $v"
echo "release tag:     $tag"
echo "major branch:    $major_branch"

check_ref_exists () {
  set +e
  git show-ref --verify --quiet "${1}" \
    && echo 1 \
    || echo 0
  set -e
}


# shallow clone does not allow to use other branches by default
# found in https://stackoverflow.com/questions/23708231/git-shallow-clone-clone-depth-misses-remote-branches
git remote set-branches ${REMOTE_NAME} '*'

echo -e "\n--- delete any local ${major_branch} branch if it exists"
set +e
git rev-parse --verify ${major_branch} 2>/dev/null && git branch -D ${major_branch}
set -e

echo -e "\n--- ensure remote tag ${tag} and branch ${major_branch} are fetched for local checkout"
git fetch --tags ${REMOTE_NAME}

if [[ '1' != $(check_ref_exists "refs/tags/${tag}") ]]; then
  echo "ERROR remote tag '${tag}' does not exists on remote '${REMOTE_NAME}'"
  exit 1
fi

checkout_options=''

echo -e "\n--- checkout or create ${major_branch} branch"
if [[ "0" == "$(check_ref_exists "refs/remotes/${REMOTE_NAME}/${major_branch}")" ]]; then
  # remote branch does not exists, creating new local branch
  checkout_options="-b ${major_branch}"
else
  # remote branch exists, create local branch to update it
  checkout_options="--track ${REMOTE_NAME}/${major_branch}"
fi
set -e

echo -e "\n--- checkout local ${major_branch} branch"
git checkout --force ${checkout_options}

echo -e "\n--- move local branch ${major_branch} to match tag ${tag}"
git reset --hard ${tag}
