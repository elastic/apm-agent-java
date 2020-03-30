#!/usr/bin/env bash

set -euxo pipefail

git tag|tail -1|sed s/v//|cut -f1 -d '.'|awk '{print $1".x"}'