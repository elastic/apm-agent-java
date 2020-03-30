#!/usr/bin/env bash

set -euxo pipefail

echo ${1}|cut -f2-3 -d '.'|{ read ver; test $ver == '0.0'; }