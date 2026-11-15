#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then
  echo 'Create .env with scripts/init-env.sh and add NVD_API_KEY.' >&2
  exit 1
fi
set -a
source ./.env
set +a
if [ -z "${NVD_API_KEY:-}" ]; then
  echo 'Add NVD_API_KEY to the local .env file.' >&2
  exit 1
fi
mkdir -p target
echo 'Running SCA; log: target/sca.log'
mvn -B -Psca -DskipTests verify 2>&1 | tee target/sca.log
