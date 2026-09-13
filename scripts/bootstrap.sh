#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -f .env ]; then echo 'Keeping existing configuration'; exit 0; fi
umask 077
mkdir -p .local
cp infra/realm.template.json .local/realm.json
for name in DB_PASSWORD IDENTITY_DB_PASSWORD RABBIT_PASSWORD ADMIN_PASSWORD DEMO_PASSWORD QA_ALICE_SECRET QA_BOB_SECRET; do
  value=$(openssl rand -hex 32)
  printf '%s=%s\n' "$name" "$value" >> .env
  sed "s/__${name}__/${value}/g" .local/realm.json > .local/realm.tmp
  mv .local/realm.tmp .local/realm.json
done
echo 'Created .env; username demo, password in DEMO_PASSWORD.'
