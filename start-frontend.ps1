[CmdletBinding()]
param(
    [switch]$SkipNpmInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Assert-Command {
    param(
        [string]$Name,
        [string]$InstallHint
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Commande '$Name' introuvable. $InstallHint"
    }
    return $command
}

Write-Host "==> Verification des prerequis frontend" -ForegroundColor Cyan
$null = Assert-Command -Name "node" -InstallHint "Installez Node.js 18 ou plus recent."
$npmCommand = Assert-Command -Name "npm.cmd" -InstallHint "Installez Node.js 18 ou plus recent."

$nodeVersion = (& node --version).TrimStart("v")
if ([int]($nodeVersion.Split(".")[0]) -lt 18) {
    throw "Node.js 18 ou plus recent est requis. Version detectee : $nodeVersion"
}

if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot ".env"))) {
    Write-Host "[INFO] .env absent : Vite utilisera VITE_API_URL par defaut si le code le permet. Copiez .env.example vers .env pour documenter votre configuration locale." -ForegroundColor Yellow
}

if (-not $SkipNpmInstall -and -not (Test-Path -LiteralPath (Join-Path $ProjectRoot "node_modules"))) {
    Push-Location $ProjectRoot
    try {
        & $npmCommand.Source ci
        if ($LASTEXITCODE -ne 0) {
            throw "npm ci a echoue."
        }
    } finally {
        Pop-Location
    }
}

Write-Host "==> Demarrage du frontend sur http://localhost:5173" -ForegroundColor Cyan
Push-Location $ProjectRoot
try {
    & $npmCommand.Source run dev -- --host 0.0.0.0
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
