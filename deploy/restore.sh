#!/usr/bin/env bash
# Restores a dump made by backup.sh. REPLACES ALL DATA in the database.
#
#   deploy/restore.sh backups/watchmark-20261106T020000Z.sql.gz
#
# Stops the backend first so nobody writes during the restore, then starts it
# again; Flyway checks the schema version on startup.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-deploy/production.env}"
compose=(docker compose -f deploy/compose.yml --env-file "$ENV_FILE")
dump="${1:?Usage: deploy/restore.sh <backup.sql.gz>}"

gzip -t "$dump"
db_name="$(grep -E '^DB_NAME=' "$ENV_FILE" | cut -d= -f2-)"
db_name="${db_name:-watchmark}"

if [[ "${CONFIRM:-}" != "$db_name" ]]; then
  read -r -p "This replaces ALL data in database '$db_name' with $dump. Type the database name to continue: " answer
  [[ "$answer" == "$db_name" ]] || { echo "Cancelled." >&2; exit 1; }
fi

"${compose[@]}" stop backend
# The dump contains DROP TABLE/CREATE TABLE for every table (--databases).
zcat "$dump" | "${compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
"${compose[@]}" start backend
echo "Restored $dump into '$db_name'; backend restarted."
