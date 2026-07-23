# Déploiement sur le VPS

Procédure de mise en production de Kam'Etud sur `kametud@51.255.164.235`,
et fonctionnement du déploiement automatique.

Site en production : **https://kametud.com**

---

## 1. Contexte de la machine

Le VPS n'est pas dédié à Kam'Etud. Les contraintes ci-dessous ont dicté
l'essentiel de la configuration.

| Contrainte | Valeur | Conséquence sur le déploiement |
|---|---|---|
| RAM | 3,7 Go **sans swap**, partagés | Un seul PostgreSQL, JVM bridées, RabbitMQ retiré |
| CPU | 2 vCPU | Les images sont construites par la CI, jamais sur le VPS |
| Outils | Ni `java` ni `mvn`, pas de sudo | Compilation impossible sur place |
| Ports réservés | 80, 443, 3000-3999 | Exposition via le Traefik déjà installé, par labels |
| Droits | `kametud` dans le groupe `docker`, Docker *rootful* | Conteneurs et ports sans sudo |

### Cohabitation

La machine héberge aussi :

- **rctt.academy** — 4 conteneurs Docker (prod, staging, dev) derrière un **Traefik v3** ;
- **une expérimentation réseau Mininet** — hors Docker, utilisateur `mininet-experiment`,
  ports 6633/6653, **sans plafond mémoire**.

C'est ce voisinage qui impose nos plafonds mémoire : un conteneur qui atteint sa
limite de cgroup est tué *lui*, avant d'épuiser la RAM système et de faire
tomber la production des autres.

---

## 2. Architecture

```
Internet
   │
   ▼
[Traefik v3]  ← déjà installé, ports 80/443, certificats Let's Encrypt
   │             (celui de rctt.academy — nous n'en modifions PAS la config)
   │
   ├── Host(`rctt.academy`)   → rctt-web-prod-1        (voisin, intact)
   └── Host(`kametud.com`)    → kametud-frontend-1
                                    │
                              [nginx] ├── /       → build React statique
                                      ├── /api/*  → api-gateway:8080  ┐
                                      └── /ws/*   → api-gateway:8080  │ réseau
                                                        │             │ privé
                                                        ▼             │ kametud_prod
                       identity / catalog / request / business /      │
                       payment / support                              │
                                          │                           │
                                          ▼                           ┘
                                    postgres (6 bases, 6 rôles)
```

Propriétés importantes :

- **Nous ne touchons ni au `traefik.yml` du voisin, ni à ses conteneurs.**
  L'exposition se fait uniquement par des **labels posés sur notre frontend**.
  Son provider Docker est en `exposedByDefault: false` : sans le label
  `traefik.enable=true`, un conteneur est ignoré. Nos huit autres services sont
  donc invisibles pour lui **par construction**.
- Le réseau `rctt_default` est déclaré `external: true` : un `docker compose down`
  de notre côté **ne peut pas le supprimer**, donc ne peut pas couper les voisins.
- **Aucun port de microservice n'est publié.** Contourner l'api-gateway — donc la
  vérification JWT et le contrôle de bannissement — est impossible.
- Le front et l'API partagent la **même origine**, donc aucun préflight CORS.
- Seul `127.0.0.1:8090` est publié, pour le diagnostic en SSH.

---

## 3. Déploiement automatique

Un `push` sur `main` met l'application à jour tout seul.

```
git push origin main
      │
      ▼
GitHub Actions construit les 8 images ──► GHCR
      │  (si UNE seule échoue, le VPS n'est pas touché)
      ▼
SSH vers le VPS ──► scripts/deploy.sh
      │
      ├── docker compose pull + up -d
      ├── vérifie /healthz et /actuator/health (jusqu'à 3 min)
      └── si KO ──► retour arrière automatique
```

### Activation

Le job de déploiement ne fait rien tant que les secrets ne sont pas renseignés.
Dans **Settings → Secrets and variables → Actions** :

| Secret | Valeur |
|---|---|
| `VPS_SSH_KEY` | clé privée de déploiement (`~/.ssh/kametud_deploy`) |
| `VPS_HOST` | `51.255.164.235` |
| `VPS_USER` | `kametud` |

### Sécurité de la clé

La clé publique est installée dans `~/.ssh/authorized_keys` avec une
**forced command** :

```
command="bash $HOME/kametud/scripts/deploy.sh",no-pty,no-port-forwarding,... ssh-ed25519 AAAA...
```

Quelle que soit la commande demandée par le client SSH, **seul `deploy.sh`
s'exécute**. Vérifié : une tentative de lecture de `.env.prod` avec cette clé
lance le déploiement au lieu d'afficher le fichier. Même compromise, elle ne
donne ni shell, ni tunnel, ni accès aux secrets.

### Déclenchement manuel

```bash
ssh kametud@51.255.164.235
bash ~/kametud/scripts/deploy.sh
```

Journal : `~/kametud/deploy.log`.

---

## 4. Installation initiale

À ne refaire qu'en cas de reconstruction complète.

### 4.1 Plugin Compose (sans root)

```bash
mkdir -p ~/.docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o ~/.docker/cli-plugins/docker-compose
chmod +x ~/.docker/cli-plugins/docker-compose
docker compose version
```

### 4.2 Fichiers

L'arborescence sur le VPS est `~/kametud/` :

```
~/kametud/
├── docker-compose.prod.yml
├── .env.prod                    (secrets, chmod 600, jamais commité)
├── postgres/init-databases.sh
└── scripts/
    ├── deploy.sh
    ├── seed-admin.sh
    └── backup.sh
```

### 4.3 Secrets

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

Générer chaque valeur vide avec `openssl rand -hex 32`, puis valider :

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml config >/dev/null && echo OK
```

> ⚠️ **Les mots de passe PostgreSQL doivent être définitifs avant le premier
> démarrage.** `postgres/init-databases.sh` ne s'exécute **qu'une seule fois**,
> à la création du volume. Les changer ensuite oblige à corriger les rôles à la
> main dans PostgreSQL.

### 4.4 Variables d'exposition

| Variable | Rôle |
|---|---|
| `PUBLIC_SITE_HOST` | `kametud.com` — construit la règle Traefik |
| `TRAEFIK_NETWORK` | `rctt_default` — réseau du Traefik existant |
| `TRAEFIK_ENABLE` | `true` en production. **`false` tant que le DNS ne pointe pas** vers la machine, sinon Let's Encrypt échoue en boucle et pollue le journal du voisin |
| `PUBLIC_BIND` | `127.0.0.1`. Passer à `0.0.0.0` expose temporairement en HTTP par IP (test sans DNS) |

### 4.5 Démarrage

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

Le service `seed` crée automatiquement `admin@kametud.com` et
`moderator@kametud.com`. Il apparaît en `Exited (0)` : c'est le résultat
**attendu** pour un conteneur à usage unique.

---

## 5. DNS et certificat

| Type | Hôte | Valeur |
|---|---|---|
| A | `@` | `51.255.164.235` |
| A | `www` | `51.255.164.235` |

Le domaine est chez **Domain.com**. Vérifier avant d'activer Traefik :

```bash
dig +short kametud.com     # doit renvoyer 51.255.164.235
```

Le certificat Let's Encrypt est obtenu **automatiquement** par Traefik au
démarrage du conteneur, et renouvelé sans intervention.

État actuel : TLS 1.3, certificat couvrant `kametud.com` et `www.kametud.com`,
redirection HTTP → HTTPS active.

---

## 6. Sauvegarder

Deux volumes contiennent tout l'état : `kametud_postgres_data` (les six bases)
et `kametud_support_storage` (fichiers téléversés).

```bash
mkdir -p ~/backups
bash ~/kametud/scripts/backup.sh
```

Le script archive les deux volumes et purge au-delà de 14 jours. Automatisation
via `crontab -e` (disponible sans root) :

```
0 3 * * * cd ~/kametud && bash scripts/backup.sh >> ~/backups/backup.log 2>&1
```

---

## 7. Diagnostic

```bash
cd ~/kametud
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f identity-service
tail -30 ~/kametud/deploy.log

# Un conteneur a-t-il été tué faute de mémoire ?
docker inspect --format '{{.Name}} OOM={{.State.OOMKilled}}' $(docker ps -aq --filter name=kametud)
```

| Symptôme | Cause probable |
|---|---|
| Redémarrages en boucle, `OOM=true` | Plafond trop bas, ou absence de swap. Voir §8 |
| Erreur 404 de Traefik | `TRAEFIK_ENABLE=false`, ou le DNS ne pointe pas ici |
| Certificat invalide | DNS pas encore propagé au moment de l'activation |
| `FATAL: password authentication failed` | `.env.prod` modifié après l'initialisation du volume (§4.3) |
| Le site affiche une ancienne version | Cache du service worker : F12 → Application → Service Workers → Unregister |
| Le déploiement auto ne part pas | Secrets GitHub absents — le job se termine proprement sans rien faire |

---

## 8. Budget mémoire

Configuration allégée pour cohabiter avec les voisins.

| Composant | Plafond | Commentaire |
|---|---|---|
| postgres | 224 Mo | Une instance, six bases. Six instances coûtaient ~800 Mo |
| identity / request / payment / support | 4 × 256 Mo | JVM bridées |
| catalog / business | 2 × 320 Mo | Relevés après mesure réelle (89 % et 82 % à 256 Mo) |
| api-gateway | 192 Mo | WebFlux, plus léger |
| frontend | 32 Mo | nginx et fichiers statiques |
| seed | 64 Mo | éphémère |
| **Total** | **≈ 2 048 Mo** | pour ~2 800 Mo disponibles |

Réglages appliqués aux JVM (`x-java-defaults` du compose) :

- `-XX:MaxRAMPercentage=50` — la JVM se cale sur le `mem_limit` du conteneur,
  pas sur les 3,7 Go de l'hôte. Sans lui, **chaque service réserverait ~925 Mo** ;
- `-XX:+UseSerialGC` — le ramasse-miettes le plus économe sur petits tas ;
- `-XX:TieredStopAtLevel=1` — compilation C1 seule, divise le cache de code ;
- `-Xss256k` — 200 threads à 1 Mo de pile par défaut, c'est 200 Mo pour rien ;
- `SPRING_MAIN_LAZY_INITIALIZATION=true` — beans créés à la demande ;
- `SERVER_TOMCAT_THREADS_MAX=20` — 200 par défaut, inutile à cette échelle ;
- `HIKARI_MAXIMUM_POOL_SIZE=5` — **le gain le plus souvent oublié** : chaque
  connexion coûte 5 à 10 Mo côté PostgreSQL, 10 par service faisaient 60 au total.

Contrepartie du *lazy initialization* : le **premier appel** à chaque écran est
plus lent de 200 à 400 ms. Avant une démonstration, parcourir l'application une
fois pour « réveiller » les services.

### Le swap reste recommandé

La machine n'a **aucun swap**, et l'expérimentation Mininet voisine n'a aucun
plafond. Demande à faire à l'administrateur root :

```
fallocate -l 2G /swapfile && chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

### Composants retirés

**RabbitMQ** a été supprimé de la production : aucun `pom.xml` ne déclare
`spring-boot-starter-amqp`, aucune classe n'utilise `RabbitTemplate` ni
`@RabbitListener`, aucun `application.yaml` ne configure `spring.rabbitmq`.
Toute la communication passe par Spring REST Client. Le conteneur consommait
~250 Mo sans rendre de service. Il reste dans `docker-compose.yml`
(développement) pour une éventuelle évolution asynchrone.

**Cloudflare Tunnel** a été envisagé puis abandonné : il répondait à l'hypothèse
d'un VPS sans reverse proxy exploitable. La découverte du Traefik existant l'a
rendu inutile — les labels évitent 96 Mo de RAM, la migration DNS vers
Cloudflare, et le passage du trafic en clair chez un tiers.

---

## 9. Sécurité — à traiter

| Point | État |
|---|---|
| Mot de passe `admin@` / `moderator@` | ⚠️ `Admin1234!` — **à changer**, le site est public et l'adresse devinable |
| Indexation Google | Ouverte (`robots.txt` permissif). À bloquer tant que le mot de passe n'est pas changé |
| `sitemap.xml` | Absent — l'URL renvoie le fallback SPA en `text/html` |
| Balise `canonical` | Absente. `www` et non-`www` répondent tous deux 200 → duplicate content |
| HSTS | Absent. À activer avec précaution : l'effet est **collant** côté navigateur |

Voir aussi : [docs/DEMARRAGE_LOCAL.md](docs/DEMARRAGE_LOCAL.md) pour l'exécution
en local, et [README_DIFFICULTES_SOLUTIONS.md](README_DIFFICULTES_SOLUTIONS.md)
pour les problèmes rencontrés et leurs solutions.
