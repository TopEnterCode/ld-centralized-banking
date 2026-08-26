#!/usr/bin/env sh
set -eu
./mvnw clean verify
if grep -R "launchdarkly-java-server-sdk" --include=pom.xml --exclude-dir=dtm-service --exclude=pom.xml .; then
  echo "LaunchDarkly server SDK dependency found outside dtm-service" >&2
  exit 1
fi
echo "Validation and dependency boundary checks passed."

