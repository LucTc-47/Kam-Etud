[CmdletBinding()]
param(
    [switch]$SeedDemo,
    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendRoot = Join-Path $ProjectRoot "Backend-Kametude"
$ComposeFile = Join-Path $BackendRoot "docker-compose.yml"
$SeedScript = Join-Path $BackendRoot "scripts\seed-demo-users.ps1"
$SmokeTestScript = Join-Path $BackendRoot "scripts\local-smoke-test.ps1"

$ExpectedServices = @(
    "identity-db",
    "catalog-db",
    "request-db",
    "business-db",
    "payment-db",
    "support-db",
    "rabbitmq",
    "identity-service",
    "catalog-service",
    "request-service",
    "business-service",
    "payment-service",
    "support-service",
    "api-gateway"
)

$ApplicationServices = @(
    "identity-service",
    "request-service",
    "catalog-service",
    "business-service",
    "payment-service",
    "support-service",
    "api-gateway"
)

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Assert-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Fichier introuvable : $Path"
    }
}

function Assert-DockerReady {
    $docker = Get-Command "docker" -ErrorAction SilentlyContinue
    if (-not $docker) {
        throw "Docker est introuvable. Installe Docker Desktop puis relance ce script."
    }

    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Docker Desktop n'est pas demarre, lance-le d'abord" -ForegroundColor Red
        exit 1
    }

    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose v2 est requis."
    }
}

function Get-ServiceState {
    param([string]$Service)

    $containerId = (& docker compose -f $ComposeFile ps -q $Service).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $containerId) {
        return [PSCustomObject]@{
            Service = $Service
            Status = "missing"
            Health = ""
            Ready = $false
        }
    }

    $status = (& docker inspect -f "{{.State.Status}}" $containerId 2>$null).Trim()
    $health = (& docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{end}}" $containerId 2>$null).Trim()
    $ready = ($health -eq "healthy") -or (-not $health -and $status -eq "running")

    return [PSCustomObject]@{
        Service = $Service
        Status = $status
        Health = $health
        Ready = $ready
    }
}

function Wait-ComposeServices {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastLineLength = 0

    while ((Get-Date) -lt $deadline) {
        $states = @($ExpectedServices | ForEach-Object { Get-ServiceState -Service $_ })
        $readyCount = @($states | Where-Object Ready).Count
        $remainingSeconds = [Math]::Max(0, [int]($deadline - (Get-Date)).TotalSeconds)
        $line = "Attente services Docker: $readyCount/$($ExpectedServices.Count) prets, timeout dans ${remainingSeconds}s"

        Write-Host ("`r" + $line.PadRight($lastLineLength)) -NoNewline -ForegroundColor Yellow
        $lastLineLength = [Math]::Max($lastLineLength, $line.Length)

        if ($readyCount -eq $ExpectedServices.Count) {
            Write-Host ""
            Write-Success "Tous les services Docker sont healthy ou running"
            return
        }

        Start-Sleep -Seconds 3
    }

    Write-Host ""
    Write-Host "Services pas encore prets apres $TimeoutSeconds secondes :" -ForegroundColor Red
    $ExpectedServices | ForEach-Object {
        $state = Get-ServiceState -Service $_
        $healthText = if ($state.Health) { $state.Health } else { "no-healthcheck" }
        $color = if ($state.Ready) { "Green" } else { "Red" }
        Write-Host ("  {0,-18} status={1,-10} health={2}" -f $state.Service, $state.Status, $healthText) -ForegroundColor $color
    }
    throw "Timeout pendant l'attente de la pile Docker Compose."
}

function Wait-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ([int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 400) {
                Write-Success "$Name repond sur $Url"
                return
            }
        } catch {
            Write-Host "." -NoNewline -ForegroundColor Yellow
        }
        Start-Sleep -Seconds 2
    }

    Write-Host ""
    throw "$Name ne repond pas sur $Url apres $TimeoutSeconds secondes."
}

function Wait-DemoLogin {
    param([int]$TimeoutSeconds = 90)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $body = @{
        email = "admin@kametud.com"
        password = "123456789!"
    } | ConvertTo-Json
    $lastError = ""

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" `
                -ContentType "application/json" -Body $body -TimeoutSec 8
            if ($response.token) {
                Write-Success "Login demo via API Gateway operationnel"
                return
            }
            $lastError = "Reponse login sans token."
        } catch {
            $lastError = $_.Exception.Message
            Write-Host "." -NoNewline -ForegroundColor Yellow
        }

        Start-Sleep -Seconds 2
    }

    Write-Host ""
    throw "Login demo impossible apres $TimeoutSeconds secondes : $lastError"
}

function Wait-ApplicationStartedLogs {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastLineLength = 0

    while ((Get-Date) -lt $deadline) {
        $started = @()
        foreach ($service in $ApplicationServices) {
            $containerId = (& docker compose -f $ComposeFile ps -q $service).Trim()
            $logs = if ($containerId) { & docker logs --tail=200 $containerId 2>$null } else { @() }
            if (($logs -join "`n") -match "\bStarted\b") {
                $started += $service
            }
        }

        $remainingSeconds = [Math]::Max(0, [int]($deadline - (Get-Date)).TotalSeconds)
        $line = "Attente demarrage Spring Boot: $($started.Count)/$($ApplicationServices.Count) applications pretes, timeout dans ${remainingSeconds}s"
        Write-Host ("`r" + $line.PadRight($lastLineLength)) -NoNewline -ForegroundColor Yellow
        $lastLineLength = [Math]::Max($lastLineLength, $line.Length)

        if ($started.Count -eq $ApplicationServices.Count) {
            Write-Host ""
            Write-Success "Tous les microservices applicatifs ont termine leur demarrage Spring Boot"
            return
        }

        Start-Sleep -Seconds 3
    }

    Write-Host ""
    Write-Host "Applications Spring Boot pas encore pretes apres $TimeoutSeconds secondes :" -ForegroundColor Red
    foreach ($service in $ApplicationServices) {
        $containerId = (& docker compose -f $ComposeFile ps -q $service).Trim()
        $logs = if ($containerId) { & docker logs --tail=200 $containerId 2>$null } else { @() }
        $ready = (($logs -join "`n") -match "\bStarted\b")
        $color = if ($ready) { "Green" } else { "Red" }
        $state = if ($ready) { "started" } else { "not-started" }
        Write-Host ("  {0,-18} {1}" -f $service, $state) -ForegroundColor $color
    }
    throw "Timeout pendant l'attente du demarrage Spring Boot des microservices."
}

try {
    Write-Step "Verification des prerequis"
    Assert-File -Path $ComposeFile
    Assert-File -Path $SmokeTestScript
    if ($SeedDemo) {
        Assert-File -Path $SeedScript
    }
    Assert-DockerReady
    Write-Success "Docker Desktop et Docker Compose sont disponibles"

    Write-Step "Demarrage de la pile Docker Compose complete"
    Push-Location $BackendRoot
    try {
        & docker compose up -d --build
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose up -d --build a echoue."
        }
    } finally {
        Pop-Location
    }

    Wait-ComposeServices
    Write-Step "Verification du demarrage Spring Boot des microservices"
    Wait-ApplicationStartedLogs
    Write-Step "Verification HTTP de l'API Gateway"
    Wait-HttpEndpoint -Name "API Gateway" -Url "http://localhost:8080/actuator/health" -TimeoutSeconds 120

    if ($SeedDemo) {
        Write-Step "Recreation des comptes demo"
        & $SeedScript -ComposeFile $ComposeFile
        if ($LASTEXITCODE -ne 0) {
            throw "Recreation des comptes demo a echoue avec le code $LASTEXITCODE."
        }
    }

    Write-Step "Verification du login demo via gateway"
    Wait-DemoLogin

    Write-Step "Smoke test bout-en-bout"
    try {
        & $SmokeTestScript
        if ($LASTEXITCODE -ne 0) {
            throw "Smoke test bout-en-bout a echoue avec le code $LASTEXITCODE."
        }
    } catch {
        $smokeError = $_.Exception.Message
        if ($smokeError -like "*Erreur MeSomb controlee avec les cles locales factices*") {
            throw "Smoke test bout-en-bout en echec sur l'assertion paiement legacy : '$smokeError'. Le Compose demo lance payment-service en mode mock, donc /api/payments/initiate peut reussir au lieu de produire l'ancienne erreur MeSomb avec cles factices."
        }
        throw "Smoke test bout-en-bout en echec : $smokeError"
    }

    Write-Host ""
    Write-Host "DEMO KAM'ETUD PRETE" -ForegroundColor Green
    Write-Host "Frontend : http://localhost:5173 (a lancer separement avec .\start-frontend.ps1)"
    Write-Host "API Gateway : http://localhost:8080"
    Write-Host "Paiements : mode mock, aucune transaction reelle"
    Write-Host "Arret backend demo : cd Backend-Kametude ; docker compose down"
} catch {
    Write-Host ""
    Write-Host "[ECHEC DEMO] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "La validation demo n'est pas terminee. Consulte les logs avec : cd Backend-Kametude ; docker compose logs --tail=100"
    exit 1
}
