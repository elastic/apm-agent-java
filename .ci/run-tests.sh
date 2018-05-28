#!/usr/bin/env bash
./mvnw clean verify --batch-mode
bash <(curl -s https://codecov.io/bash)
