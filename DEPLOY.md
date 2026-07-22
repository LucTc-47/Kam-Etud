# Deploiement sur le VPS

Procedure de mise en production de Kam'Etud sur la machine `kametud@<IP>`.

---

## 1. Contexte de la machine

Le VPS n'est pas dedie a Kam'Etud. Les contraintes ci-dessous ont dicte
l'essentiel de la configuration.

| Contrainte | Valeur | Consequence sur le deploiement |
|---|---|---|
| RAM | 3,7 Go, dont ~1 Go deja pris, **sans swap** | Un seul Postgres, JVM plafonnees, RabbitMQ retire |
| CPU | 2 vCPU | Les images sont construites par la CI, jamais sur le VPS |
| Outils | Ni `java` ni `mvn`, pas de sudo | Idem : compilation impossible sur place |
| Ports reserves | **80, 443, 3000-3999**, tenus par une application **tierce** | Exposition par tunnel Cloudflare : aucun port entrant, aucune config a demander a autrui |
| Droits | `kametud` est dans le groupe `docker`, Docker est *rootful* | Les conteneurs demarrent sans sudo |

---

## 2. Architecture retenue

```
Internet
   |
   v
[Cloudflare]  <- termine le HTTPS, certificat gere et renouvele automatiquement
   |
   |  connexion SORTANTE, initiee depuis le VPS
   v
[cloudflared] --> [frontend] nginx
                     |-- /        -> build React statique
                     |-- /api/*   -> api-gateway:8080  \
                     |-- /ws/*    -> api-gateway:8080  /  reseau Docker « kametud »
                                          |
                                          +-- identity / catalog / request / business / payment / support
                                                          |
                                                          +-- postgres (6 bases, 6 roles)
```

Pourquoi ce montage plutot qu'un reverse proxy classique :

- Les ports **80, 443 et 3000-3999 appartiennent a une application tierce** que
  nous ne pouvons pas reconfigurer, et l'utilisateur `kametud` n'a pas root.
  Le tunnel n'ouvre **aucun port entrant** : il se connecte vers l'exterieur.
  Le conflit disparait au lieu d'etre contourne.
- **Aucun port de microservice n'est publie.** Les ports 8081 a 8086 ne sont plus
  exposes : contourner l'api-gateway, donc la verification JWT et le controle de
  bannissement, est devenu impossible.
- **Le front et l'API partagent la meme origine**, donc aucun preflight CORS.
- Le seul port publie, `127.0.0.1:8090`, sert uniquement au diagnostic en SSH.
  Il n'est pas joignable depuis l'exterieur et ne porte aucun trafic public.

---

## 3. Mise en place du tunnel Cloudflare

A faire une seule fois, entierement sans root et sans intervention d'un tiers.

### 3.1 Rattacher le domaine a Cloudflare

Le domaine est achete chez Namecheap ; il y reste. Seuls les serveurs de noms
changent, l'operation est gratuite.

1. Creer un compte sur <https://dash.cloudflare.com> et **Add a site** :
   `kametud.com`, plan **Free**.
2. Cloudflare importe les enregistrements DNS existants et affiche deux
   serveurs de noms, du type `xxx.ns.cloudflare.com`.
3. Dans Namecheap : **Domain List** > `kametud.com` > **Manage** > section
   **Nameservers** > choisir **Custom DNS** et saisir les deux valeurs.
4. La propagation prend de quelques minutes a 24 h. Cloudflare envoie un mail
   quand le domaine est actif.

### 3.2 Creer le tunnel

1. Cloudflare Zero Trust > **Networks** > **Tunnels** > **Create a tunnel**.
2. Type **Cloudflared**, nommer le tunnel (par exemple `kametud-vps`).
3. L'ecran suivant affiche une commande d'installation contenant un long jeton.
   **Ne pas lancer cette commande** : notre `cloudflared` tourne en conteneur.
   Copier uniquement le jeton (la valeur apres `--token`).
4. Onglet **Public Hostnames** > **Add a public hostname** :

   | Champ | Valeur |
   |---|---|
   | Subdomain | *(vide)* |
   | Domain | `kametud.com` |
   | Type | `HTTP` |
   | URL | `frontend:8080` |

   `frontend` est le nom du conteneur nginx, resolu par le reseau Docker interne.
   Le type est bien **HTTP** et non HTTPS : le chiffrement s'arrete chez
   Cloudflare, la liaison interne reste en clair dans le reseau Docker.

5. Repeter pour `www.kametud.com` avec la meme cible.

### 3.3 Renseigner le jeton

Coller le jeton dans `.env.prod` :

```
CLOUDFLARE_TUNNEL_TOKEN=eyJhIjoiXXXXXXXX...
```

Apres `docker compose up -d`, le tunnel doit apparaitre **HEALTHY** dans le
tableau de bord Cloudflare.

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml logs cloudflared | tail -20
```

### 3.4 Reglages Cloudflare recommandes

- **SSL/TLS** > mode **Full**. En mode *Flexible*, Cloudflare accepterait du
  HTTP non chiffre depuis n'importe ou ; le tunnel etant deja chiffre de bout en
  bout jusqu'a Cloudflare, *Full* est correct et plus sur.
- **SSL/TLS** > **Edge Certificates** > activer **Always Use HTTPS**.
- Le plan gratuit limite les televersements a 100 Mo, largement au-dessus des
  25 Mo autorises par notre nginx.

---

## 4. La seule demande a faire a l'administrateur root

Un fichier de swap de 2 Go. La somme des plafonds memoire de la stack est de
**2,96 Go** pour **2,8 Go disponibles**. Sans swap, le noyau tue un conteneur
des que plusieurs services montent en charge simultanement.

> Merci d'ajouter 2 Go de swap :
> ```
> fallocate -l 2G /swapfile && chmod 600 /swapfile
> mkswap /swapfile && swapon /swapfile
> echo '/swapfile none swap sw 0 0' >> /etc/fstab
> ```

Sans ce swap, le deploiement fonctionnera au repos mais restera fragile.
L'alternative propre est de passer le VPS a 8 Go.

---

## 5. Installation initiale

### 5.1 Installer le plugin Compose (sans root)

`docker compose` n'est pas installe sur la machine. En plugin utilisateur :

```bash
mkdir -p ~/.docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o ~/.docker/cli-plugins/docker-compose
chmod +x ~/.docker/cli-plugins/docker-compose
docker compose version
```

### 5.2 Recuperer le depot

```bash
cd ~
git clone https://github.com/LucTc-47/Kam-Etud.git
cd Kam-Etud/Backend-Kametude
```

### 5.3 S'authentifier sur GHCR

Necessaire uniquement si le depot est prive. Creer un *Personal Access Token*
GitHub avec la portee `read:packages`, puis :

```bash
echo 'ghp_xxxxxxxxxxxx' | docker login ghcr.io -u LucTc-47 --password-stdin
```

### 5.4 Renseigner les secrets

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

Generer une valeur pour **chaque** champ vide :

```bash
openssl rand -hex 32
```

`.env.prod` est ignore par git et ne doit jamais etre committe.
Verifier que rien ne manque — le demarrage echoue explicitement sur toute
variable absente, grace a la syntaxe `${VAR:?}` :

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml config >/dev/null && echo "configuration valide"
```

### 5.5 Demarrer

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

Avec 2 vCPU, comptez **3 a 5 minutes** avant que les sept JVM soient pretes.

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
curl -s http://127.0.0.1:8080/actuator/health   # gateway
curl -s http://127.0.0.1:8090/healthz           # frontend, en local
curl -s https://kametud.com/healthz             # frontend, via le tunnel
```

La derniere commande est celle qui compte : elle valide toute la chaine, de
Cloudflare jusqu'au conteneur nginx.

> **Le script d'initialisation des bases ne s'execute qu'une seule fois**, quand
> le volume `postgres_data` est vide. Si les identifiants de `.env.prod` changent
> apres coup, il faut modifier les roles dans Postgres a la main : reecrire
> `.env.prod` ne suffira pas.

---

## 6. Mettre a jour

Un `push` sur `main` declenche le workflow `publish-images.yml`, qui publie les
huit images sur GHCR. Ensuite, sur le VPS :

```bash
cd ~/Kam-Etud/Backend-Kametude
git pull                                                     # recupere le compose a jour
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker image prune -f                                        # libere le disque
```

### Revenir en arriere

Chaque image est aussi taguee avec le SHA du commit. Pour repointer la stack sur
une version anterieure, editer `IMAGE_TAG` dans `.env.prod` :

```bash
IMAGE_TAG=sha-3f2a1b9c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f90
```

puis relancer `pull` et `up -d`.

---

## 7. Sauvegarder les donnees

Deux volumes contiennent l'integralite de l'etat : `postgres_data` (les six
bases) et `support_storage` (les fichiers televerses).

```bash
# Sauvegarde des six bases dans un seul fichier
docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T postgres \
  pg_dumpall -U "$(grep POSTGRES_ADMIN_USER .env.prod | cut -d= -f2)" \
  | gzip > ~/backups/kametud-$(date +%F).sql.gz
```

```bash
# Sauvegarde des fichiers televerses
docker run --rm -v kametud_support_storage:/data -v ~/backups:/out alpine \
  tar czf /out/storage-$(date +%F).tar.gz -C /data .
```

Le script `scripts/backup.sh` fait les deux, purge les archives de plus de
14 jours et se lance a la main ou via `crontab -e` (disponible sans root) :

```bash
mkdir -p ~/backups
bash scripts/backup.sh
```

```
0 3 * * * cd ~/Kam-Etud/Backend-Kametude && bash scripts/backup.sh >> ~/backups/backup.log 2>&1
```

---

## 8. Diagnostic

```bash
# Consommation memoire reelle, service par service
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'

# Logs d'un service
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f identity-service

# Un conteneur a-t-il ete tue par manque de memoire ?
docker inspect --format '{{.Name}} OOMKilled={{.State.OOMKilled}} exit={{.State.ExitCode}}' $(docker ps -aq)
```

| Symptome | Cause probable |
|---|---|
| Un service redemarre en boucle, `OOMKilled=true` | Plafond memoire trop bas, ou absence de swap. Voir §4 |
| `502` en local sur 8090 | La gateway n'est pas encore prete (compter 3 a 5 min au demarrage) |
| Erreur 1033 ou 502 de Cloudflare | Le tunnel est deconnecte, ou le *public hostname* ne pointe pas sur `frontend:8080`. Verifier `docker compose logs cloudflared` et l'etat du tunnel dans le tableau de bord |
| Le site repond en local mais pas sur le domaine | Les serveurs de noms Namecheap ne pointent pas encore sur Cloudflare, ou la propagation n'est pas terminee. Verifier avec `dig NS kametud.com` |
| `FATAL: password authentication failed` | `.env.prod` a change apres l'initialisation du volume. Voir l'avertissement du §5.5 |
| Le site affiche l'ancienne version | Cache du service worker : `sw.js` et `index.html` sont pourtant servis en `no-cache`, forcer un rechargement dur |

---

## 9. Budget memoire

| Composant | Plafond | Commentaire |
|---|---|---|
| postgres | 320 Mo | Une instance, six bases. Six instances separees coutaient ~800 Mo |
| identity / catalog / request / business / payment / support | 6 x 360 Mo | `-XX:MaxRAMPercentage=50` + SerialGC |
| api-gateway | 320 Mo | WebFlux, plus leger |
| frontend | 64 Mo | nginx et fichiers statiques |
| cloudflared | 96 Mo | Agent du tunnel. Remplace un reverse proxy qu'on ne pourrait pas installer |
| **Total** | **2 960 Mo** | pour **~2 800 Mo disponibles** |

**Le total des plafonds depasse la memoire libre d'environ 160 Mo.** Ce n'est pas
une erreur de calcul : un `mem_limit` est un plafond, pas une reservation, et les
services ne l'atteignent jamais tous en meme temps. En pratique la stack
consomme plutot 2,2 a 2,4 Go au repos.

Mais **cela ne laisse aucune marge**, et sans swap le noyau n'a aucune soupape :
il tue directement un conteneur. Le swap du §4 est ce qui separe une stack stable
d'une stack qui perd un service au premier pic de trafic. A verifier des la mise
en ligne :

```bash
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'
```

Si un service depasse durablement 85 % de son plafond, l'augmenter et en baisser
un autre plutot que de laisser le noyau arbitrer.

### Composants retires

**RabbitMQ** a ete supprime du compose de production. Aucun `pom.xml` ne declare
`spring-boot-starter-amqp`, aucune classe n'utilise `RabbitTemplate` ou
`@RabbitListener`, et aucun `application.yaml` ne configure `spring.rabbitmq` :
toute la communication inter-services passe par Spring REST Client. Le conteneur
consommait environ 250 Mo sans rendre de service. Il reste present dans
`docker-compose.yml` (developpement) pour le jour ou une file d'attente sera
reellement branchee.
