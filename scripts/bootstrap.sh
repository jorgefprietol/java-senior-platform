#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -f .env ]; then
  chmod 700 .local
  chmod 600 .env
  chmod 644 .local/realm.json
  echo 'Keeping existing configuration'
  exit 0
fi
umask 077
mkdir -p .local
cp infra/realm.template.json .local/realm.json
for name in DB_PASSWORD IDENTITY_DB_PASSWORD RABBIT_PASSWORD ADMIN_PASSWORD DEMO_PASSWORD QA_ALICE_SECRET QA_BOB_SECRET; do
  value=$(openssl rand -hex 32)
  printf '%s=%s\n' "$name" "$value" >> .env
  sed "s/__${name}__/${value}/g" .local/realm.json > .local/realm.tmp
  mv .local/realm.tmp .local/realm.json
done
# Keycloak runs as a different UID. The read-only bind-mounted file must be
# readable by that UID; the 0700 parent directory keeps it private on the host.
chmod 700 .local
chmod 600 .env
chmod 644 .local/realm.json
echo 'Created .env; username demo, password in DEMO_PASSWORD.'
