#!/usr/bin/env bash
#
# Configure the committer since the maven release requires to push changes to GitHub
# This will help with the SLSA requirements.
#
set -euo pipefail

echo "--- Configure git context :git:"

git config --global user.email "infra-root+apmmachine@elastic.co"
git config --global user.name "apmmachine"
