#!/usr/bin/env bash

# This script sets commonly used environment variables for the release build
#
# Must be sourced and not run directly or variables will not be exported.

export TAG_VER=$(git tag | tail -1 | sed s/v//)
export TAG_DOT_X=$(echo $TAG_VER |cut -f1 -d '.'|awk '{print $1".x"}')