#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"${repository_root}/gradlew" \
  --project-dir "${repository_root}" \
  publishToMavenCentralPortal

"${repository_root}/gradlew" \
  --project-dir "${repository_root}/gradle-plugin" \
  publishAndReleaseToMavenCentral
