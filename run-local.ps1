# Lance toute la plateforme Kam'Etud en local, PUIS cree les comptes
# admin@kametud.com et moderator@kametud.com. Une seule commande pour tout.
#
#   .\run-local.ps1
#
# Etapes enchainees :
#   1. start-local.ps1  -> 6 bases PostgreSQL (Docker) + 7 microservices Java
#                          + frontend React sur http://localhost:5173
#   2. seed-admin-local.ps1 -> comptes admin et moderateur
#
# Prerequis : Docker Desktop demarre, JDK 21, Node.js >= 18. Voir
# docs/DEMARRAGE_LOCAL.md pour le detail et le depannage.

[CmdletBinding()]
param(
    # Ne demarre pas le frontend React (backend seul).
    [switch]$BackendOnly,
    # Saute « npm ci » si node_modules est deja installe (demarrage plus rapide).
    [switch]$SkipNpmInstall,
    # Mot de passe attribue aux deux comptes crees.
    [string]$AdminPassword = "Admin1234!",
    # Ne cree pas les comptes admin/moderateur (lance seulement la stack).
    [switch]$NoSeed
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Phase { param([string]$Message) Write-Host "`n########## $Message ##########`n" -ForegroundColor Magenta }

Write-Phase "1/2  Demarrage de la stack locale"
& (Join-Path $ProjectRoot "start-local.ps1") -BackendOnly:$BackendOnly -SkipNpmInstall:$SkipNpmInstall
if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
    throw "Le demarrage de la stack a echoue. Les comptes ne seront pas crees."
}

if ($NoSeed) {
    Write-Host "`n[INFO] Option -NoSeed : comptes admin/moderateur non crees." -ForegroundColor Yellow
    return
}

Write-Phase "2/2  Creation des comptes admin et moderateur"
& (Join-Path $ProjectRoot "seed-admin-local.ps1") -Password $AdminPassword
if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
    throw "La creation des comptes a echoue (la stack, elle, tourne)."
}

Write-Host "`n==================================================" -ForegroundColor Green
Write-Host " KAM'ETUD EST PRET" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
if (-not $BackendOnly) {
    Write-Host " Frontend : http://localhost:5173"
}
Write-Host " Gateway  : http://localhost:8080"
Write-Host ""
Write-Host " Connexion :"
Write-Host "   admin@kametud.com     / $AdminPassword"
Write-Host "   moderator@kametud.com / $AdminPassword"
Write-Host ""
Write-Host " Arret : .\start-local.ps1 -Stop" -ForegroundColor DarkGray
Write-Host " Arret + bases : .\start-local.ps1 -Stop -StopDatabases" -ForegroundColor DarkGray
