# Démarrage en local

Comment lancer toute la plateforme Kam'Etud sur ta machine, avec des comptes
administrateur et modérateur prêts à l'emploi.

---

## En une commande

Depuis la racine du projet, dans un terminal **PowerShell** :

```powershell
.\run-local.ps1
```

Ce script fait tout :

1. démarre les **6 bases PostgreSQL** dans Docker ;
2. démarre les **7 microservices** Java (identity, catalog, request, business, payment, support, gateway) ;
3. démarre le **frontend React** ;
4. crée les comptes **admin** et **modérateur**.

Compter **2 à 4 minutes**. Le script attend que chaque service réponde avant de continuer.

Quand c'est prêt :

| | |
|---|---|
| **Site** | http://localhost:5173 |
| **API Gateway** | http://localhost:8080 |

### Comptes créés

| Email | Mot de passe | Rôle | Redirigé après connexion vers |
|---|---|---|---|
| `admin@kametud.com` | `Admin1234!` | admin | `/admin` |
| `moderator@kametud.com` | `Admin1234!` | modérateur | `/moderateur` |

Pour un compte **client**, utilise le formulaire d'inscription du site : il est redirigé vers `/services`.

---

## Prérequis

| Outil | Version | Vérifier | Installer |
|---|---|---|---|
| **Docker Desktop** | démarré | `docker info` | <https://www.docker.com/products/docker-desktop/> |
| **JDK** | exactement **21** | `java --version` | Temurin 21 |
| **Node.js** | **18** ou plus | `node --version` | <https://nodejs.org/> |

> Maven n'est **pas** requis : le script le télécharge tout seul si absent.
>
> Le piège le plus fréquent : avoir un JDK différent de 21. Le script refuse de
> démarrer si `java --version` n'affiche pas 21.

---

## Options de `run-local.ps1`

```powershell
.\run-local.ps1 -BackendOnly        # backend seul, sans le frontend React
.\run-local.ps1 -SkipNpmInstall     # ne réinstalle pas node_modules (plus rapide)
.\run-local.ps1 -NoSeed             # lance la stack sans créer les comptes
.\run-local.ps1 -AdminPassword "MonMotDePasse!"   # autre mot de passe pour les 2 comptes
```

---

## Arrêter

```powershell
.\start-local.ps1 -Stop                  # arrête services + frontend, garde les bases (et leurs données)
.\start-local.ps1 -Stop -StopDatabases   # arrête tout, sans effacer les données
```

Les données survivent à un arrêt : les bases sont dans des volumes Docker. Elles
ne sont perdues que par un `docker compose down -v` explicite.

---

## Recréer seulement les comptes

Si la stack tourne déjà et que tu veux juste (re)créer les comptes :

```powershell
.\seed-admin-local.ps1
```

Le script est **idempotent** : le relancer ne crée pas de doublon, il remet à
jour le mot de passe et le rôle. Il accepte aussi `-Password "..."`.

---

## Deux modes de lancement (pour information)

Le projet propose deux façons de tourner en local — `run-local.ps1` utilise la première.

| | `start-local.ps1` (utilisé par `run-local`) | `start-demo.ps1` |
|---|---|---|
| Microservices | processus Java natifs (`mvn spring-boot:run`) | conteneurs Docker |
| Bases | Docker | Docker |
| Rapidité de redémarrage | rapide (pas de rebuild d'image) | plus lent (build Docker) |
| Idéal pour | développer au quotidien | valider la pile complète comme en prod |

---

## Dépannage

| Symptôme | Cause / solution |
|---|---|
| `Java 21 est requis` | Un autre JDK est actif. Installe Temurin 21 et vérifie `java --version`. |
| `Ports déjà occupés : 8080, …` | Une pile tourne déjà. Fais `.\start-local.ps1 -Stop` d'abord. |
| `Docker Desktop ne répond pas` | Démarre Docker Desktop et attends qu'il soit prêt. |
| `Erreur API 403` à la connexion | Origine CORS non autorisée — ne concerne pas le local standard (localhost:5173 est autorisé). |
| Connexion refusée / comptes absents | La base n'était pas prête au moment du seed. Relance `.\seed-admin-local.ps1`. |
| Un service ne démarre pas | Les logs sont dans `logs\local\<date>\<service>.err.log`. |

---

## Note sécurité

Ces comptes et mots de passe sont destinés au **développement local uniquement**.
Ne jamais réutiliser `Admin1234!` sur un environnement accessible depuis Internet.
Les paiements tournent en mode **mock** (simulés, aucun argent réel).
