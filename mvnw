#!/usr/bin/env sh
set -eu

BUILD_IMAGE="centralized-banking-build-env:java21-node22"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Maven is not installed and Docker is unavailable." >&2
  exit 1
fi

docker build --target build-env -t "$BUILD_IMAGE" . >/dev/null

exec docker run --rm \
  -v "$(pwd):/workspace" \
  -v "centralized-banking-m2:/root/.m2" \
  -w /workspace \
  "$BUILD_IMAGE" mvn "$@"
