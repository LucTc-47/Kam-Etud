#!/bin/bash
# Initialisation du Postgres unique de production.
#
# Ancienne topologie : six conteneurs postgres:16-alpine, un par microservice.
# Sur un VPS de 3,7 Go partage avec une autre application, ces six instances
# coutaient environ 800 Mo a elles seules. Une instance unique heberge
# desormais les six bases, ce qui ramene le cout a environ 200 Mo.
#
# L'isolation est conservee : chaque service garde son utilisateur dedie,
# proprietaire de sa seule base. Le service Catalog ne peut pas lire
# identity_db, exactement comme avant la fusion.
#
# Ce script n'est execute par l'image postgres que si le volume de donnees
# est vide, c'est-a-dire au tout premier demarrage. Modifier ce fichier
# n'a aucun effet sur une base deja initialisee.

set -euo pipefail

# Les mots de passe transitent par du SQL. On double les apostrophes pour
# qu'un mot de passe en contenant une ne casse pas la requete.
sql_escape() {
  printf '%s' "$1" | sed "s/'/''/g"
}

create_service_database() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  if [ -z "$db_user" ] || [ -z "$db_password" ]; then
    echo "init-databases: identifiants manquants pour $db_name" >&2
    exit 1
  fi

  local escaped_password
  escaped_password="$(sql_escape "$db_password")"

  echo "init-databases: creation de $db_name (proprietaire $db_user)"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
	CREATE ROLE "$db_user" WITH LOGIN PASSWORD '$escaped_password';
	CREATE DATABASE "$db_name" OWNER "$db_user";
	EOSQL

  # Depuis PostgreSQL 15, le schema public n'est plus inscriptible par defaut
  # pour un role qui ne le possede pas. Les services tournent en
  # ddl-auto: update et doivent pouvoir creer leurs tables : on transfere donc
  # la propriete du schema au role du service.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db_name" <<-EOSQL
	ALTER SCHEMA public OWNER TO "$db_user";
	REVOKE ALL ON DATABASE "$db_name" FROM PUBLIC;
	EOSQL
}

create_service_database "identity_db" "${IDENTITY_DB_USERNAME:-}" "${IDENTITY_DB_PASSWORD:-}"
create_service_database "catalog_db"  "${CATALOG_DB_USERNAME:-}"  "${CATALOG_DB_PASSWORD:-}"
create_service_database "request_db"  "${REQUEST_DB_USERNAME:-}"  "${REQUEST_DB_PASSWORD:-}"
create_service_database "business_db" "${BUSINESS_DB_USERNAME:-}" "${BUSINESS_DB_PASSWORD:-}"
create_service_database "payment_db"  "${PAYMENT_DB_USERNAME:-}"  "${PAYMENT_DB_PASSWORD:-}"
create_service_database "support_db"  "${SUPPORT_DB_USERNAME:-}"  "${SUPPORT_DB_PASSWORD:-}"

echo "init-databases: les six bases sont pretes"
