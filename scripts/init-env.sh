#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then
  echo '.env already exists; leaving it unchanged.'
  exit 0
fi
umask 077
set -C
cat > .env <<EOF
POSTGRES_DB=secure_notes
POSTGRES_USER=notes_app
POSTGRES_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
DEMO_USERNAME=student
DEMO_PASSWORD=$(openssl rand -hex 12)
EOF
echo 'Created .env with local credentials.'
