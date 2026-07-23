# Cree (ou remet a jour) les comptes admin@kametud.com et moderator@kametud.com
# sur la base locale, avec le mot de passe de ton choix.
#
# Ces deux roles ne peuvent PAS etre obtenus par le formulaire d'inscription :
# AuthService.register refuse ADMIN et MODERATOR pour qu'un visiteur ne puisse
# pas se declarer administrateur. Il faut donc les inserer directement en base.
#
# La stack locale doit tourner (base identity-db demarree) avant de lancer ce
# script. Utilise plutot .\run-local.ps1 qui enchaine tout automatiquement.
#
# Le mot de passe est hache cote PostgreSQL avec pgcrypto (crypt + gen_salt bf),
# ce qui produit un hash $2a$ compatible avec le BCryptPasswordEncoder de Spring.
# Aucun hash n'est donc a pre-calculer : le script accepte n'importe quel mot
# de passe.

[CmdletBinding()]
param(
    [string]$ComposeFile,
    [string]$IdentityDbService = "identity-db",
    [string]$DbName = "identity_db",
    [string]$PgUser = $(if ($env:IDENTITY_DB_USERNAME) { $env:IDENTITY_DB_USERNAME } else { "user" }),
    [string]$Password = "Admin1234!"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ComposeFile) {
    $ComposeFile = Join-Path $ScriptRoot "Backend-Kametude\docker-compose.yml"
}

function Write-Step { param([string]$Message) Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Write-Success { param([string]$Message) Write-Host "[OK] $Message" -ForegroundColor Green }

function Escape-SqlLiteral {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    return $Value.Replace("'", "''")
}

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    throw "docker-compose.yml introuvable : $ComposeFile"
}
$null = Get-Command docker -ErrorAction Stop

$escapedPassword = Escape-SqlLiteral $Password

# Un seul aller-retour SQL, transactionnel : les deux comptes et leurs profils
# sont crees ou mis a jour ensemble. Relancer le script ne cree pas de doublon
# (ON CONFLICT), il remet juste le mot de passe et le role a jour.
$sql = @"
CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH seed_users(email, role_name, first_name, last_name) AS (
    VALUES
        ('admin@kametud.com',     'ADMIN',     'Admin',      'Kametud'),
        ('moderator@kametud.com', 'MODERATOR', 'Moderateur', 'Kametud')
),
upsert_users AS (
    INSERT INTO users (id, email, password, role, enabled)
    SELECT gen_random_uuid(), email, crypt('$escapedPassword', gen_salt('bf', 10)), role_name, true
    FROM seed_users
    ON CONFLICT (email) DO UPDATE
        SET password = EXCLUDED.password,
            role = EXCLUDED.role,
            enabled = true
    RETURNING id, email
)
INSERT INTO profiles (
    id, user_id, first_name, last_name, email, role, verified, banned, created_at, updated_at
)
SELECT
    gen_random_uuid(), u.id, s.first_name, s.last_name, s.email,
    lower(s.role_name), true, false, now(), now()
FROM seed_users s
JOIN upsert_users u ON u.email = s.email
ON CONFLICT (user_id) DO UPDATE
    SET first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        email = EXCLUDED.email,
        role = EXCLUDED.role,
        verified = true;
"@

Write-Step "Creation des comptes admin@ et moderator@ sur $DbName"

# -T : pas de pseudo-TTY, indispensable quand la commande est scriptee.
# Le SQL est passe via -c (comme seed-demo-users.ps1) plutot que par stdin,
# ce qui evite les soucis d'encodage du pipe PowerShell.
& docker compose -f $ComposeFile exec -T $IdentityDbService `
    psql -U $PgUser -d $DbName -v ON_ERROR_STOP=1 -q -c $sql
if ($LASTEXITCODE -ne 0) {
    throw "Insertion SQL impossible. La base '$IdentityDbService' est-elle demarree ? (docker compose ps)"
}

Write-Step "Verification en base"
$check = & docker compose -f $ComposeFile exec -T $IdentityDbService `
    psql -U $PgUser -d $DbName -t -A -F ' | ' `
    -c "SELECT email, role FROM users WHERE email IN ('admin@kametud.com','moderator@kametud.com') ORDER BY email;"
$check | Where-Object { $_ } | ForEach-Object { Write-Host "  $_" -ForegroundColor Green }

Write-Success "Comptes prets"
Write-Host ""
Write-Host "  admin@kametud.com     / $Password  (role admin)"
Write-Host "  moderator@kametud.com / $Password  (role moderator)"
Write-Host ""
Write-Host "Ces identifiants sont destines au developpement local uniquement." -ForegroundColor Yellow
