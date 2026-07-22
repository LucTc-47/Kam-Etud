#!/bin/bash
# Cree les comptes admin@kametud.com et moderator@kametud.com.
#
# Ces roles ne peuvent pas etre obtenus par le formulaire d'inscription :
# AuthService.register refuse explicitement ADMIN et MODERATOR pour empecher
# qu'un visiteur se declare administrateur. Il faut donc les creer ici.
#
# Ce script est lance automatiquement par le service « seed » de
# docker-compose.prod.yml, apres que la Gateway soit declaree saine.
# Il est idempotent : chaque « up -d » le rejoue sans rien casser.
#
# Execution manuelle possible :
#   docker compose --env-file .env.prod -f docker-compose.prod.yml run --rm seed
#
# Il tourne dans une image postgres:16-alpine, qui fournit psql, bash et le
# wget de BusyBox. Pas de curl : d'ou l'usage de « wget --post-data ».
#
# Methode : inscription par l'API publique, puis promotion du role en base.
#
# Ancienne approche possible : un INSERT SQL direct avec un hash BCrypt fige,
# comme le fait scripts/seed-demo-users.ps1 en developpement. Elle est evitee
# ici car register() cree aussi le profil et le lie au user par UUID. Un INSERT
# partiel donnerait un compte sans profil, et login() echouerait sur
# « Profil non trouve » — panne difficile a diagnostiquer un jour de demo.

set -uo pipefail

API_URL="${API_URL:-http://api-gateway:8080}"
SEED_PASSWORD="${SEED_PASSWORD:-Admin1234!}"

export PGHOST="${PGHOST:-postgres}"
export PGDATABASE="${PGDATABASE:-identity_db}"
export PGUSER="${PGUSER:?PGUSER manquant}"
export PGPASSWORD="${PGPASSWORD:?PGPASSWORD manquant}"

run_sql() {
  psql -v ON_ERROR_STOP=1 -q -c "$1"
}

# Renvoie le code HTTP d'un POST JSON. BusyBox wget n'a pas d'equivalent au
# « -w %{http_code} » de curl : sur erreur il ecrit « server returned error:
# HTTP/1.1 409 CONFLICT » sur stderr, d'ou l'extraction par motif.
post_json() {
  local url="$1" payload="$2" out
  out=$(wget -q -O - --post-data="$payload" \
        --header="Content-Type: application/json" "$url" 2>&1)
  if printf '%s' "$out" | grep -q '"token"'; then
    echo "201"
  else
    printf '%s' "$out" | grep -oE 'HTTP/1\.[01] [0-9]{3}' | head -1 | awk '{print $2}'
  fi
}

# La Gateway met 3 a 5 minutes a repondre au premier demarrage sur 2 vCPU.
# Le healthcheck de Compose couvre deja ce delai, mais l'attente reste utile
# lors d'une execution manuelle.
echo "seed-admin: attente de l'API sur $API_URL"
for i in $(seq 1 60); do
  if wget -q -O /dev/null "$API_URL/actuator/health" 2>/dev/null; then
    echo "seed-admin: API prete"
    break
  fi
  if [ "$i" = "60" ]; then
    echo "seed-admin: l'API ne repond pas apres 5 minutes" >&2
    exit 1
  fi
  sleep 5
done

seed_account() {
  local email="$1" role_upper="$2" role_lower="$3" first_name="$4" last_name="$5"

  echo
  echo "--- $email ---"

  # 1. Inscription par l'API : hachage BCrypt et creation du profil lie.
  #    Le role demande est volontairement CLIENT, le seul que l'API accepte.
  #
  #    Les services tournent en SPRING_MAIN_LAZY_INITIALIZATION : leurs beans
  #    ne sont instancies qu'au premier appel. La Gateway peut donc etre saine
  #    alors que l'identity-service initialise encore JPA, et la premiere
  #    requete repond alors 500. On reessaie plutot que d'abandonner.
  local code payload attempt
  payload="{\"email\":\"$email\",\"password\":\"$SEED_PASSWORD\",\"role\":\"CLIENT\",\"firstName\":\"$first_name\",\"lastName\":\"$last_name\"}"

  for attempt in 1 2 3 4 5 6; do
    code=$(post_json "$API_URL/api/auth/register" "$payload")
    case "$code" in
      201) echo "  compte cree"; break ;;
      409) echo "  compte deja present, promotion quand meme"; break ;;
      *)
        if [ "$attempt" = "6" ]; then
          echo "  echec de l'inscription apres 6 tentatives (HTTP ${code:-inconnu})" >&2
          return 1
        fi
        echo "  HTTP ${code:-inconnu}, service pas encore pret, nouvelle tentative dans 10 s ($attempt/6)"
        sleep 10
        ;;
    esac
  done

  # 2. Promotion. Les deux tables doivent etre mises a jour : users porte
  #    l'enum en majuscules et pilote Spring Security, profiles porte la chaine
  #    en minuscules que le frontend lit pour afficher les menus admin.
  run_sql "UPDATE users SET role = '$role_upper' WHERE email = '$email';" || return 1
  run_sql "UPDATE profiles SET role = '$role_lower', verified = true WHERE email = '$email';" || return 1
  echo "  role promu en $role_upper"

  # 3. Verification reelle : on se reconnecte et on relit le role renvoye.
  #    Sans cela le script pourrait « reussir » sur un compte inutilisable.
  local body login_role
  body=$(wget -q -O - --post-data="{\"email\":\"$email\",\"password\":\"$SEED_PASSWORD\"}" \
         --header="Content-Type: application/json" "$API_URL/api/auth/login" 2>&1)
  login_role=$(printf '%s' "$body" | grep -oE '"role":"[a-z]+"' | head -1 | cut -d'"' -f4)

  if [ "$login_role" = "$role_lower" ]; then
    echo "  connexion verifiee, role renvoye : $login_role"
  else
    echo "  ATTENTION : connexion impossible ou role inattendu (recu '${login_role:-rien}')" >&2
    return 1
  fi
}

failures=0
seed_account "admin@kametud.com"     "ADMIN"     "admin"     "Admin"      "Kametud"   || failures=$((failures + 1))
seed_account "moderator@kametud.com" "MODERATOR" "moderator" "Moderateur" "Kametud"   || failures=$((failures + 1))

echo
if [ "$failures" -ne 0 ]; then
  echo "seed-admin: $failures compte(s) en echec" >&2
  exit 1
fi

echo "seed-admin: termine."
echo "  admin@kametud.com     / $SEED_PASSWORD"
echo "  moderator@kametud.com / $SEED_PASSWORD"
echo
echo "Identifiants volontairement simples pour la demonstration."
echo "A changer avant tout usage reel : la plateforme est publique."
