#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv app_password DUNE_PASSWORD
CREATE ROLE dune LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE dunerealms TO dune;
GRANT USAGE, CREATE ON SCHEMA public TO dune;
SQL
