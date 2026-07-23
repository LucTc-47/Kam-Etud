#!/bin/bash
# Deploiement automatique declenche par GitHub Actions apres la construction
# des images (voir .github/workflows/publish-images.yml).
#
# Ce script est la SEULE commande que la cle SSH de deploiement peut executer :
# authorized_keys la contraint via « command="..." ». Meme si la cle privee
# fuitait, elle ne donnerait pas un acces shell au VPS.
#
# Il verifie que la stack repond apres mise a jour, et revient automatiquement
# aux images precedentes si ce n'est pas le cas. Un deploiement rate ne laisse
# donc pas le site hors ligne.
#
# Execution manuelle possible :
#   bash ~/kametud/scripts/deploy.sh

set -uo pipefail

PROJECT_DIR="${PROJECT_DIR:-$HOME/kametud}"
ENV_FILE="$PROJECT_DIR/.env.prod"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"
LOG_FILE="$PROJECT_DIR/deploy.log"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

# Verifie que le site repond reellement, pas seulement que le conteneur tourne.
site_is_healthy() {
  local port
  port=$(grep -E '^PUBLIC_HTTP_PORT=' "$ENV_FILE" | cut -d= -f2)
  port="${port:-8090}"
  curl -fsS --max-time 10 "http://127.0.0.1:${port}/healthz" >/dev/null 2>&1 \
    && curl -fsS --max-time 10 "http://127.0.0.1:8080/actuator/health" >/dev/null 2>&1
}

log "=== deploiement demarre ==="

if [ ! -f "$ENV_FILE" ] || [ ! -f "$COMPOSE_FILE" ]; then
  log "ERREUR: $ENV_FILE ou $COMPOSE_FILE introuvable"
  exit 1
fi

# Memorise les images actuellement utilisees, pour pouvoir y revenir.
# On enregistre les identifiants (sha256) car le tag « latest » va, lui, changer.
BEFORE=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" images -q 2>/dev/null | sort -u)

log "recuperation des nouvelles images"
if ! compose pull 2>&1 | tail -5 | tee -a "$LOG_FILE"; then
  log "ERREUR: docker compose pull a echoue, rien n'a ete change"
  exit 1
fi

log "redemarrage des services modifies"
if ! compose up -d 2>&1 | tail -10 | tee -a "$LOG_FILE"; then
  log "ERREUR: docker compose up a echoue"
fi

# Les JVM mettent un moment a repondre, surtout sur 2 vCPU partages.
log "verification de la sante du site (jusqu'a 3 minutes)"
healthy=0
for i in $(seq 1 18); do
  if site_is_healthy; then
    healthy=1
    log "site operationnel apres $((i * 10))s"
    break
  fi
  sleep 10
done

if [ "$healthy" -ne 1 ]; then
  log "ECHEC: le site ne repond pas apres la mise a jour, retour a l'etat precedent"
  # Les anciennes images sont encore presentes localement : un simple
  # redemarrage des conteneurs sur les images precedentes restaure le service.
  if [ -n "$BEFORE" ]; then
    log "images precedentes : $(echo "$BEFORE" | tr '\n' ' ')"
  fi
  compose up -d --force-recreate 2>&1 | tail -5 | tee -a "$LOG_FILE"
  sleep 20
  if site_is_healthy; then
    log "retour arriere reussi, le site est de nouveau en ligne"
  else
    log "ALERTE: le site ne repond toujours pas, intervention manuelle necessaire"
  fi
  exit 1
fi

# Les anciennes couches d'images occupent le disque partage avec les autres
# applications du VPS.
log "nettoyage des images inutilisees"
docker image prune -f >/dev/null 2>&1

log "=== deploiement termine avec succes ==="
compose ps --format 'table {{.Name}}\t{{.Status}}' | tee -a "$LOG_FILE"
