#!/usr/bin/env bash
# Local dev runner — sources .env.dev and starts Spring Boot.
# Usage: ./scripts/dev.sh
set -euo pipefail

if [ ! -f .env.dev ]; then
  echo "❌ .env.dev not found. Copy .env.example to .env.dev and fill in dev values."
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env.dev
set +a

exec ./gradlew bootRun
