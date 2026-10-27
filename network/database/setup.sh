#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
command -v docker >/dev/null || { echo 'Install Docker Engine with the Compose plugin first.' >&2; exit 1; }
docker compose version >/dev/null
umask 077
if [[ ! -e .env ]]; then
  command -v openssl >/dev/null
  {
    printf 'POSTGRES_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 32)"
    printf 'DUNE_PASSWORD=%s\n' "$(openssl rand -hex 32)"
    printf 'DB_BIND_IP=127.0.0.1\n'
  } > .env
fi
docker compose up -d --wait
printf '\nDatabase ready. Configure every backend using the dune password in this directory/.env.\nSee README.md for network binding, migration, backups and recovery.\n'
