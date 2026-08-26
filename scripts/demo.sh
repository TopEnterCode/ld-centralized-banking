#!/usr/bin/env sh
set -eu
export POC_MODE="${POC_MODE:-mock}"
docker compose up --build

