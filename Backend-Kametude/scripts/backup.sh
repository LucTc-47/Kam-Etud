#!/bin/bash
# Sauvegarde des deux volumes qui contiennent tout l'etat de production :
# les six bases PostgreSQL et les fichiers televerses par le support-service.
#
# Utilisation depuis Backend-Kametude/ :
#   bash scripts/backup.sh
#
# Invoque via « bash » plutot que « ./ » : le depot se developpe sous Windows,
# qui ne conserve pas le bit d'execution.
#
# Automatisation (crontab -e, disponible sans root) :
#   0 3 * * * cd ~/Kam-Etud/Backend-Kametude && bash scripts/backup.sh >> ~/backups/backup.log 2>&1

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_DIR/.env.prod"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
# Une sauvegarde qu'on ne purge jamais finit par remplir les 38 Go du disque,
# partages avec l'autre application du VPS.
RETENTION_DAYS="${RETENTION_DAYS:-14}"

if [ ! -f "$ENV_FILE" ]; then
  echo "backup: $ENV_FILE introuvable" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
stamp="$(date +%F-%H%M)"

# Lu depuis .env.prod plutot que code en dur : le role admin est le seul
# habilite a faire un dump des six bases d'un coup.
admin_user="$(grep -E '^POSTGRES_ADMIN_USER=' "$ENV_FILE" | cut -d= -f2-)"
if [ -z "$admin_user" ]; then
  echo "backup: POSTGRES_ADMIN_USER absent de $ENV_FILE" >&2
  exit 1
fi

echo "backup: dump des bases vers kametud-db-$stamp.sql.gz"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  pg_dumpall -U "$admin_user" \
  | gzip > "$BACKUP_DIR/kametud-db-$stamp.sql.gz"

echo "backup: archive des fichiers vers kametud-storage-$stamp.tar.gz"
docker run --rm \
  -v kametud_support_storage:/data:ro \
  -v "$BACKUP_DIR:/out" \
  alpine tar czf "/out/kametud-storage-$stamp.tar.gz" -C /data .

echo "backup: suppression des sauvegardes de plus de $RETENTION_DAYS jours"
find "$BACKUP_DIR" -name 'kametud-*' -type f -mtime "+$RETENTION_DAYS" -delete

echo "backup: termine"
ls -lh "$BACKUP_DIR" | tail -5
