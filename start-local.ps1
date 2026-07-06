[CmdletBinding()]
param(
    [switch]$Stop,
    [switch]$StopDatabases,
    [switch]$BackendOnly,
    [switch]$SkipNpmInstall,
    [ValidateRange(30, 600)]
    [int]$StartupTimeoutSeconds = 180,
    [ValidateRange(1024, 65535)]
    [int]$IdentityDbPort = 5431,
    [ValidateRange(1024, 65535)]
    [int]$CatalogDbPort = 5632,
    [ValidateRange(1024, 65535)]
    [int]$RequestDbPort = 5633,
    [ValidateRange(1024, 65535)]
    [int]$BusinessDbPort = 5634,
    [ValidateRange(1024, 65535)]
    [int]$PaymentDbPort = 5635,
    [ValidateRange(1024, 65535)]
    [int]$SupportDbPort = 5636
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendRoot = Join-Path $ProjectRoot "Backend-Kametude"
$ComposeFile = Join-Path $BackendRoot "docker-compose.yml"
$MavenProperties = Join-Path $BackendRoot "identity-service\.mvn\wrapper\maven-wrapper.properties"
$StateRoot = Join-Path $ProjectRoot "logs\local"
$StateFile = Join-Path $StateRoot "processes.json"
$PowerShellExecutable = (Get-Process -Id $PID).Path
$script:StartedProcesses = @()

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

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

function Resolve-MavenExecutable {
    $globalMaven = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($globalMaven) {
        return $globalMaven.Source
    }

    if (-not (Test-Path -LiteralPath $MavenProperties)) {
        throw "La configuration Maven Wrapper est introuvable : $MavenProperties"
    }

    $wrapperProperties = Get-Content -LiteralPath $MavenProperties -Raw | ConvertFrom-StringData
    $distributionUrl = $wrapperProperties.distributionUrl
    if (-not $distributionUrl -or $distributionUrl -notmatch "-bin\.zip$") {
        throw "URL de distribution Maven invalide dans maven-wrapper.properties."
    }

    $archiveName = Split-Path $distributionUrl -Leaf
    $distributionName = $archiveName -replace "-bin\.zip$", ""
    $installRoot = Join-Path $ProjectRoot ".tools\maven"
    $mavenExecutable = Join-Path $installRoot "$distributionName\bin\mvn.cmd"
    if (Test-Path -LiteralPath $mavenExecutable) {
        return $mavenExecutable
    }

    Write-Host "[INFO] Maven n'est pas installe : telechargement automatique de $distributionName..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
    $archivePath = Join-Path $installRoot $archiveName
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $webClient = [System.Net.WebClient]::new()
        try {
            $webClient.DownloadFile($distributionUrl, $archivePath)
        } finally {
            $webClient.Dispose()
        }
        Expand-Archive -LiteralPath $archivePath -DestinationPath $installRoot -Force
    } finally {
        Remove-Item -LiteralPath $archivePath -Force -ErrorAction SilentlyContinue
    }

    if (-not (Test-Path -LiteralPath $mavenExecutable)) {
        throw "Maven a ete telecharge, mais mvn.cmd reste introuvable."
    }
    return $mavenExecutable
}

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    foreach ($rawLine in Get-Content -LiteralPath $Path) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }

        $parts = $line.Split(@("="), 2, [System.StringSplitOptions]::None)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($name -and -not [Environment]::GetEnvironmentVariable($name, "Process")) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Set-LocalDefault {
    param(
        [string]$Name,
        [string]$Value,
        [string]$PlaceholderPattern = "^$"
    )

    $current = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not $current -or ($PlaceholderPattern -and $current -match $PlaceholderPattern)) {
        [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
}

function Test-TcpPort {
    param(
        [int]$Port,
        [int]$TimeoutMilliseconds = 350
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $connection.AsyncWaitHandle.WaitOne($TimeoutMilliseconds)) {
            return $false
        }
        $client.EndConnect($connection)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-TcpPort {
    param(
        [string]$Name,
        [int]$Port,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -Port $Port) {
            Write-Success "$Name ecoute sur le port $Port"
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "$Name n'a pas ouvert le port $Port apres $TimeoutSeconds secondes."
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
            # Le service peut encore etre en train d'initialiser Hibernate.
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name ne repond pas sur $Url apres $TimeoutSeconds secondes."
}

function Save-ProcessState {
    if (-not (Test-Path -LiteralPath $StateRoot)) {
        New-Item -ItemType Directory -Path $StateRoot -Force | Out-Null
    }

    [PSCustomObject]@{
        startedAt = (Get-Date).ToString("o")
        processes = @($script:StartedProcesses | ForEach-Object {
            [PSCustomObject]@{
                name = $_.Name
                processId = $_.Process.Id
                port = $_.Port
                stdout = $_.Stdout
                stderr = $_.Stderr
            }
        })
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StateFile -Encoding UTF8
}

function Start-ManagedProcess {
    param(
        [string]$Name,
        [string]$Command,
        [string]$WorkingDirectory,
        [int]$Port,
        [hashtable]$Environment = @{},
        [string]$LogDirectory
    )

    $previousValues = @{}
    foreach ($entry in $Environment.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, "Process")
        [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, "Process")
    }

    try {
        $stdout = Join-Path $LogDirectory "$Name.out.log"
        $stderr = Join-Path $LogDirectory "$Name.err.log"
        $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
        $process = Start-Process `
            -FilePath $PowerShellExecutable `
            -ArgumentList @("-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", $encodedCommand) `
            -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr `
            -WindowStyle Hidden `
            -PassThru

        $processEntry = [PSCustomObject]@{
            Name = $Name
            Process = $process
            Port = $Port
            Stdout = $stdout
            Stderr = $stderr
        }
        $script:StartedProcesses += $processEntry
        Save-ProcessState
        Write-Host "[START] $Name (PID $($process.Id), logs: $stdout)" -ForegroundColor Yellow
        return $processEntry
    } finally {
        foreach ($entry in $Environment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $previousValues[$entry.Key], "Process")
        }
    }
}

function Stop-ProcessTree {
    param([int]$ProcessId)

    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId ([int]$child.ProcessId)
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Stop-LocalStack {
    Write-Step "Arret du frontend et des microservices"
    if (Test-Path -LiteralPath $StateFile) {
        $state = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        foreach ($managedProcess in @($state.processes) | Sort-Object processId -Descending) {
            if (Get-Process -Id ([int]$managedProcess.processId) -ErrorAction SilentlyContinue) {
                Stop-ProcessTree -ProcessId ([int]$managedProcess.processId)
                Write-Host "[STOP] $($managedProcess.name)" -ForegroundColor Yellow
            }
        }
        Remove-Item -LiteralPath $StateFile -Force -ErrorAction SilentlyContinue
        Write-Success "Processus locaux arretes"
    } else {
        Write-Host "Aucun processus lance par ce script n'est enregistre."
    }

    if ($StopDatabases) {
        Write-Step "Arret des bases PostgreSQL Docker"
        & docker compose -f $ComposeFile stop identity-db catalog-db request-db business-db payment-db support-db
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose n'a pas pu arreter les bases."
        }
        Write-Success "Bases arretees sans supprimer leurs donnees"
    }
}

function Show-RecentLogs {
    foreach ($managedProcess in $script:StartedProcesses) {
        Write-Host "`n--- $($managedProcess.Name) ---" -ForegroundColor DarkYellow
        if (Test-Path -LiteralPath $managedProcess.Stderr) {
            Get-Content -LiteralPath $managedProcess.Stderr -Tail 20
        }
        if (Test-Path -LiteralPath $managedProcess.Stdout) {
            Get-Content -LiteralPath $managedProcess.Stdout -Tail 20
        }
    }
}

if ($Stop) {
    Stop-LocalStack
    exit 0
}

try {
    Write-Step "Verification des prerequis"
    if (-not (Test-Path -LiteralPath $ComposeFile)) {
        throw "Backend-Kametude/docker-compose.yml est introuvable. Lancez le script depuis un clone complet."
    }
    if (-not (Test-Path -LiteralPath $MavenProperties)) {
        throw "La configuration Maven Wrapper de identity-service est introuvable."
    }

    $null = Assert-Command -Name "java" -InstallHint "Installez un JDK 21 et ajoutez java au PATH."
    $null = Assert-Command -Name "node" -InstallHint "Installez Node.js 18 ou plus recent."
    $null = Assert-Command -Name "docker" -InstallHint "Installez Docker Desktop puis demarrez-le."
    $npmCommand = Assert-Command -Name "npm.cmd" -InstallHint "Installez Node.js 18 ou plus recent."

    $javaVersion = (& java --version | Select-Object -First 1)
    if ($javaVersion -notmatch "\b21(?:\.|\s)") {
        throw "Java 21 est requis. Version detectee : $javaVersion"
    }

    $nodeVersion = (& node --version).TrimStart("v")
    if ([int]($nodeVersion.Split(".")[0]) -lt 18) {
        throw "Node.js 18 ou plus recent est requis. Version detectee : $nodeVersion"
    }

    & docker info | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop ne repond pas. Demarrez Docker puis relancez ce script."
    }
    & docker compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose v2 est requis."
    }
    Write-Success "Java $javaVersion, Node $nodeVersion et Docker sont disponibles"

    if (Test-Path -LiteralPath $StateFile) {
        $existingState = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        $alive = @($existingState.processes | Where-Object {
            Get-Process -Id ([int]$_.processId) -ErrorAction SilentlyContinue
        })
        if ($alive.Count -gt 0) {
            Write-Host "La pile locale est deja lancee. Utilisez '.\start-local.ps1 -Stop' avant de la relancer." -ForegroundColor Yellow
            exit 0
        }
        Remove-Item -LiteralPath $StateFile -Force
    }

    $applicationPorts = 8080, 8081, 8082, 8083, 8084, 8085, 8086, 5173
    $occupiedPorts = @($applicationPorts | Where-Object { Test-TcpPort -Port $_ })
    if ($occupiedPorts.Count -gt 0) {
        throw "Ports deja occupes : $($occupiedPorts -join ', '). Arretez les processus existants; si ce script les a lances, utilisez '.\start-local.ps1 -Stop'."
    }

    $mavenExecutable = Resolve-MavenExecutable
    Write-Success "Maven disponible : $mavenExecutable"

    Write-Step "Chargement de la configuration locale"
    Import-DotEnv -Path (Join-Path $BackendRoot ".env")
    Set-LocalDefault -Name "JWT_SECRET" -Value "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970" -PlaceholderPattern "^REMPLACER_"
    Set-LocalDefault -Name "INTERNAL_SERVICE_TOKEN" -Value "change-this-internal-token"
    Set-LocalDefault -Name "PAYMENT_PROVIDER" -Value "mock"
    Set-LocalDefault -Name "MESOMB_BASE_URL" -Value "https://business.mesomb.com/fr/api/v1.1"
    Set-LocalDefault -Name "MESOMB_APPLICATION_KEY" -Value "local-development-app" -PlaceholderPattern "^REMPLACER_"
    Set-LocalDefault -Name "MESOMB_ACCESS_KEY" -Value "local-development-access" -PlaceholderPattern "^REMPLACER_"
    Set-LocalDefault -Name "MESOMB_SECRET_KEY" -Value "local-development-secret" -PlaceholderPattern "^REMPLACER_"

    $paymentProvider = [Environment]::GetEnvironmentVariable("PAYMENT_PROVIDER", "Process")
    $paymentIsLocal = [Environment]::GetEnvironmentVariable("MESOMB_APPLICATION_KEY", "Process") -like "local-development-*"
    if ($paymentProvider -eq "mock") {
        Write-Host "[INFO] PAYMENT_PROVIDER=mock : paiements simules localement, aucun argent reel." -ForegroundColor Yellow
    } elseif ($paymentIsLocal) {
        Write-Host "[INFO] Cles MeSomb factices : le service demarrera, mais aucun paiement reel ne fonctionnera." -ForegroundColor Yellow
    }

    Write-Step "Installation des dependances frontend"
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
        Write-Success "Dependances npm installees"
    } elseif ($SkipNpmInstall) {
        Write-Host "[INFO] Installation npm ignoree par option."
    } else {
        Write-Success "Dependances npm deja presentes"
    }

    Write-Step "Demarrage des six bases PostgreSQL"
    $composeVariables = @{
        IDENTITY_DB_PORT = [string]$IdentityDbPort
        CATALOG_DB_PORT = [string]$CatalogDbPort
        REQUEST_DB_PORT = [string]$RequestDbPort
        BUSINESS_DB_PORT = [string]$BusinessDbPort
        PAYMENT_DB_PORT = [string]$PaymentDbPort
        SUPPORT_DB_PORT = [string]$SupportDbPort
    }
    $previousComposeValues = @{}
    foreach ($entry in $composeVariables.GetEnumerator()) {
        $previousComposeValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, "Process")
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
    }
    try {
        & docker compose -f $ComposeFile up -d identity-db catalog-db request-db business-db payment-db support-db
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose n'a pas pu demarrer PostgreSQL."
        }
    } finally {
        foreach ($entry in $composeVariables.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $previousComposeValues[$entry.Key], "Process")
        }
    }

    Wait-TcpPort -Name "identity-db" -Port $IdentityDbPort -TimeoutSeconds 60
    Wait-TcpPort -Name "catalog-db" -Port $CatalogDbPort -TimeoutSeconds 60
    Wait-TcpPort -Name "request-db" -Port $RequestDbPort -TimeoutSeconds 60
    Wait-TcpPort -Name "business-db" -Port $BusinessDbPort -TimeoutSeconds 60
    Wait-TcpPort -Name "payment-db" -Port $PaymentDbPort -TimeoutSeconds 60
    Wait-TcpPort -Name "support-db" -Port $SupportDbPort -TimeoutSeconds 60

    $runId = Get-Date -Format "yyyyMMdd-HHmmss"
    $logDirectory = Join-Path $StateRoot $runId
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $escapedMaven = $mavenExecutable.Replace("'", "''")

    function New-MavenCommand {
        param([string]$ServiceDirectory)
        $pom = (Join-Path $BackendRoot "$ServiceDirectory\pom.xml").Replace("'", "''")
        return "& '$escapedMaven' --no-transfer-progress -f '$pom' spring-boot:run"
    }

    $jwtSecret = [Environment]::GetEnvironmentVariable("JWT_SECRET", "Process")
    $internalToken = [Environment]::GetEnvironmentVariable("INTERNAL_SERVICE_TOKEN", "Process")
    $commonEnvironment = @{
        JWT_SECRET = $jwtSecret
        INTERNAL_SERVICE_TOKEN = $internalToken
    }

    Write-Step "Demarrage d'Identity et Catalog"
    Start-ManagedProcess -Name "identity" -Command (New-MavenCommand "identity-service") `
        -WorkingDirectory (Join-Path $BackendRoot "identity-service") -Port 8081 -LogDirectory $logDirectory `
        -Environment ($commonEnvironment + @{ DB_URL = "jdbc:postgresql://localhost:$IdentityDbPort/identity_db" }) | Out-Null
    Start-ManagedProcess -Name "catalog" -Command (New-MavenCommand "catalog-service") `
        -WorkingDirectory (Join-Path $BackendRoot "catalog-service") -Port 8083 -LogDirectory $logDirectory `
        -Environment ($commonEnvironment + @{ DB_URL = "jdbc:postgresql://localhost:$CatalogDbPort/catalog_db" }) | Out-Null
    Wait-TcpPort -Name "Identity Service" -Port 8081 -TimeoutSeconds $StartupTimeoutSeconds
    Wait-TcpPort -Name "Catalog Service" -Port 8083 -TimeoutSeconds $StartupTimeoutSeconds

    Write-Step "Demarrage des services metier"
    Start-ManagedProcess -Name "request" -Command (New-MavenCommand "requestservice") `
        -WorkingDirectory (Join-Path $BackendRoot "requestservice") -Port 8082 -LogDirectory $logDirectory `
        -Environment ($commonEnvironment + @{ DB_URL = "jdbc:postgresql://localhost:$RequestDbPort/request_db" }) | Out-Null
    Start-ManagedProcess -Name "business" -Command (New-MavenCommand "business-service") `
        -WorkingDirectory (Join-Path $BackendRoot "business-service") -Port 8084 -LogDirectory $logDirectory `
        -Environment ($commonEnvironment + @{ DB_URL = "jdbc:postgresql://localhost:$BusinessDbPort/business_db" }) | Out-Null
    Start-ManagedProcess -Name "payment" -Command (New-MavenCommand "payment-service") `
        -WorkingDirectory (Join-Path $BackendRoot "payment-service") -Port 8085 -LogDirectory $logDirectory `
        -Environment ($commonEnvironment + @{
            DB_URL = "jdbc:postgresql://localhost:$PaymentDbPort/payment_db"
            PAYMENT_PROVIDER = [Environment]::GetEnvironmentVariable("PAYMENT_PROVIDER", "Process")
            MESOMB_BASE_URL = [Environment]::GetEnvironmentVariable("MESOMB_BASE_URL", "Process")
            MESOMB_APPLICATION_KEY = [Environment]::GetEnvironmentVariable("MESOMB_APPLICATION_KEY", "Process")
            MESOMB_ACCESS_KEY = [Environment]::GetEnvironmentVariable("MESOMB_ACCESS_KEY", "Process")
            MESOMB_SECRET_KEY = [Environment]::GetEnvironmentVariable("MESOMB_SECRET_KEY", "Process")
        }) | Out-Null
    Start-ManagedProcess -Name "support" -Command (New-MavenCommand "support-service") `
        -WorkingDirectory (Join-Path $BackendRoot "support-service") -Port 8086 -LogDirectory $logDirectory `
        -Environment ($commonEnvironment + @{ DB_URL = "jdbc:postgresql://localhost:$SupportDbPort/support_db" }) | Out-Null

    Wait-TcpPort -Name "Request Service" -Port 8082 -TimeoutSeconds $StartupTimeoutSeconds
    Wait-TcpPort -Name "Business Service" -Port 8084 -TimeoutSeconds $StartupTimeoutSeconds
    Wait-TcpPort -Name "Payment Service" -Port 8085 -TimeoutSeconds $StartupTimeoutSeconds
    Wait-TcpPort -Name "Support Service" -Port 8086 -TimeoutSeconds $StartupTimeoutSeconds

    Write-Step "Demarrage de l'API Gateway"
    Start-ManagedProcess -Name "gateway" -Command (New-MavenCommand "api-gateway") `
        -WorkingDirectory (Join-Path $BackendRoot "api-gateway") -Port 8080 -LogDirectory $logDirectory `
        -Environment $commonEnvironment | Out-Null
    Wait-HttpEndpoint -Name "API Gateway" -Url "http://localhost:8080/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds

    if (-not $BackendOnly) {
        Write-Step "Demarrage du frontend React"
        $escapedNpm = $npmCommand.Source.Replace("'", "''")
        $frontendCommand = "& '$escapedNpm' run dev -- --host 0.0.0.0"
        Start-ManagedProcess -Name "frontend" -Command $frontendCommand -WorkingDirectory $ProjectRoot `
            -Port 5173 -LogDirectory $logDirectory -Environment @{} | Out-Null
        Wait-HttpEndpoint -Name "Frontend" -Url "http://localhost:5173" -TimeoutSeconds 60
    } else {
        Write-Host "[INFO] Option -BackendOnly : frontend React non demarre." -ForegroundColor Yellow
    }

    Write-Host "`nKam'Etud est pret." -ForegroundColor Green
    if (-not $BackendOnly) {
        Write-Host "Frontend : http://localhost:5173"
    } else {
        Write-Host "Frontend : non demarre (-BackendOnly)"
    }
    Write-Host "Gateway  : http://localhost:8080"
    Write-Host "Logs     : $logDirectory"
    Write-Host "Arret    : .\start-local.ps1 -Stop"
    Write-Host "Bases    : .\start-local.ps1 -Stop -StopDatabases"
} catch {
    Write-Host "`n[ERREUR] $($_.Exception.Message)" -ForegroundColor Red
    Show-RecentLogs
    foreach ($managedProcess in $script:StartedProcesses | Sort-Object { $_.Process.Id } -Descending) {
        Stop-ProcessTree -ProcessId $managedProcess.Process.Id
    }
    Remove-Item -LiteralPath $StateFile -Force -ErrorAction SilentlyContinue
    exit 1
}
