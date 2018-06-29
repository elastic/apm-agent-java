#!/usr/bin/env bash
set -e
./mvnw clean verify --batch-mode
bash <(curl -s https://codecov.io/bash)
