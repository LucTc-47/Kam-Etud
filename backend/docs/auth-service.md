# Auth Service

## Responsabilité

Gestion de l’authentification et de la sécurité.

## Fonctionnalités

- Inscription utilisateur
- Connexion JWT
- OTP SMS
- Refresh Token
- Gestion des rôles
- Validation email/téléphone

## Endpoints

### POST /api/auth/register

Créer un compte.

### POST /api/auth/login

Connexion utilisateur.

### POST /api/auth/verify-otp

Validation OTP.

### POST /api/auth/refresh

Renouveler token JWT.

## Base de données

### Table users_credentials

| Champ | Type |
|---|---|
| id | UUID |
| email | VARCHAR |
| password | VARCHAR |
| role | ENUM |
| otp_code | VARCHAR |

## Technologies Spring

- Spring Security
- JWT
- BCrypt
- Spring Validation

## Sécurité

- Password hashing
- JWT expiration
- Rate limiting