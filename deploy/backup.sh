#!/usr/bin/env bash
# Nightly database backup. Writes a compressed, consistent dump of the running
# database to $BACKUP_DIR and deletes dumps older than $KEEP_DAYS.
#
#   deploy/backup.sh                      # from the repo root on the server
#   KEEP_DAYS=30 deploy/backup.sh
#
# Copy the dumps off the server too (see docs/deployment.md): a backup that
# lives only on the machine it protects is lost with the machine.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-deploy/production.env}"
BACKUP_DIR="${BACKUP_DIR:-backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
compose=(docker compose -f deploy/compose.yml --env-file "$ENV_FILE")

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
file="$BACKUP_DIR/watchmark-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
tmp="$file.partial"

# --single-transaction: a consistent snapshot without locking the app out.
# The password stays inside the container (MYSQL_PWD), never on a command line.
"${compose[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces --databases "$MYSQL_DATABASE"' \
  | gzip -9 > "$tmp"

# A truncated dump must never look like a good one.
gzip -t "$tmp"
zcat "$tmp" | tail -n 1 | grep -q -- '-- Dump completed' || { echo "Backup incomplete: $tmp" >&2; exit 1; }
mv "$tmp" "$file"
chmod 600 "$file"

find "$BACKUP_DIR" -name 'watchmark-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "Backup written: $file ($(du -h "$file" | cut -f1))"
