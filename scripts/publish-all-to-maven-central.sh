#!/usr/bin/env bash
set -euo pipefail

# Avoid AGP AndroidLocationsException if both ANDROID_PREFS_ROOT and ANDROID_USER_HOME are set
unset ANDROID_PREFS_ROOT || true

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"${repository_root}/gradlew" \
  --project-dir "${repository_root}" \
  publishToMavenCentralPortal

"${repository_root}/gradlew" \
  --project-dir "${repository_root}/gradle-plugin" \
  publishAndReleaseToMavenCentral
