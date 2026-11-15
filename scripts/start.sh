#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test -f .env || sh scripts/init-env.sh
mvn -B -DskipTests package
docker compose up -d --build --wait
attempt=0
until [ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/data || true)" = 401 ]; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo 'API did not become ready; check docker compose logs api.' >&2
    exit 1
  fi
  sleep 2
done
echo 'API ready at http://localhost:8080'
