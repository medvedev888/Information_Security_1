#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test -f .env || sh scripts/init-env.sh
set -a
. ./.env
set +a
export TEST_DB_URL="jdbc:postgresql://localhost:5433/$POSTGRES_DB"
export TEST_DB_USERNAME="$POSTGRES_USER"
export TEST_DB_PASSWORD="$POSTGRES_PASSWORD"
docker compose up -d --wait postgres
mvn -B verify
