#!/usr/bin/env bash
# Create the study_track role and database on the local PostgreSQL (the same server Folio Pro uses).
# Flyway creates the tables when the API starts. Safe to run more than once.
set -euo pipefail

db_name="${POSTGRES_DB:-study_track}"
db_user="${POSTGRES_USER:-study_track}"
db_password="${POSTGRES_PASSWORD:-admin}"
admin_user="${PG_ADMIN_USER:-postgres}"
psql_admin=(psql -v ON_ERROR_STOP=1 -h "${PG_HOST:-localhost}" -p "${POSTGRES_HOST_PORT:-5432}" -U "$admin_user" -d postgres)

if [[ ! $db_name =~ ^[a-z_][a-z0-9_]*$ || ! $db_user =~ ^[a-z_][a-z0-9_]*$ ]]; then
  printf 'POSTGRES_DB and POSTGRES_USER must be lower-case identifiers.\n' >&2
  exit 2
fi

if [[ "$("${psql_admin[@]}" -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$db_user'")" != "1" ]]; then
  "${psql_admin[@]}" -c "CREATE ROLE $db_user LOGIN PASSWORD '${db_password//\'/\'\'}'"
fi
if [[ "$("${psql_admin[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname = '$db_name'")" != "1" ]]; then
  "${psql_admin[@]}" -c "CREATE DATABASE $db_name OWNER $db_user"
fi
printf 'Database %s is ready for user %s.\n' "$db_name" "$db_user"
