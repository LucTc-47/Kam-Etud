[CmdletBinding()]
param(
    [string]$ComposeFile,
    [string]$IdentityDbService = "identity-db",
    [string]$DbName = "identity_db",
    [string]$DbUser = "user",
    [string]$PasswordLabel = "123456789!",
    [string]$PasswordHash = '$2a$10$cpbx6PcDGMXWKfzizdcf.OHObQ1WvE9oq23xUTo1JwHMagC02/Z6q'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendRoot = Resolve-Path (Join-Path $ScriptRoot "..")
if (-not $ComposeFile) {
    $ComposeFile = Join-Path $BackendRoot "docker-compose.yml"
}

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Escape-SqlLiteral {
    param([string]$Value)
    if ($null -eq $Value) {
        return ""
    }
    return $Value.Replace("'", "''")
}

function Invoke-IdentitySql {
    param([string]$Sql)

    & docker compose -f $ComposeFile exec -T $IdentityDbService `
        psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -q -c $Sql

    if ($LASTEXITCODE -ne 0) {
        throw "Execution SQL impossible. Verifiez que Docker Desktop est lance et que '$IdentityDbService' est demarre."
    }
}

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    throw "docker-compose.yml introuvable : $ComposeFile"
}

$null = Get-Command docker -ErrorAction Stop

$hash = Escape-SqlLiteral $PasswordHash

Write-Step "Creation ou mise a jour des comptes demo Kam'Etud"

$sql = @"
CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH seed_users(email, role_name, first_name, last_name, phone, city, university, faculty, level, bio, verified, banned) AS (
    VALUES
        ('admin@kametud.com', 'ADMIN', 'Admin', 'Kametud', '237690000001', 'Dschang', NULL, NULL, NULL, 'Compte administrateur local de demonstration.', true, false),
        ('moderator@kametud.com', 'MODERATOR', 'Moderateur', 'Kametud', '237690000002', 'Dschang', NULL, NULL, NULL, 'Compte moderateur local de demonstration.', true, false),
        ('student@kametud.com', 'STUDENT', 'luc', 'tc', '237655000000', 'Yaounde', 'Universite de Dschang', 'informatique', 'licence3', 'devops junior', true, false),
        ('client@kametud.com', 'CLIENT', 'derrick', 'chebou', '237670000000', 'Douala', NULL, NULL, NULL, 'Compte client local de demonstration.', true, false)
),
upsert_users AS (
    INSERT INTO users (id, email, password, role, enabled)
    SELECT gen_random_uuid(), email, '$hash', role_name, true
    FROM seed_users
    ON CONFLICT (email) DO UPDATE
        SET password = EXCLUDED.password,
            role = EXCLUDED.role,
            enabled = true
    RETURNING id, email
)
INSERT INTO profiles (
    id, user_id, first_name, last_name, email, phone, avatar_url, bio, city,
    university, faculty, level, rating, role, verified, banned, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    u.id,
    s.first_name,
    s.last_name,
    s.email,
    s.phone,
    NULL,
    s.bio,
    s.city,
    s.university,
    s.faculty,
    s.level,
    0,
    lower(s.role_name),
    s.verified,
    s.banned,
    now(),
    now()
FROM seed_users s
JOIN users u ON u.email = s.email
ON CONFLICT (user_id) DO UPDATE
    SET first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        email = EXCLUDED.email,
        phone = EXCLUDED.phone,
        bio = EXCLUDED.bio,
        city = EXCLUDED.city,
        university = EXCLUDED.university,
        faculty = EXCLUDED.faculty,
        level = EXCLUDED.level,
        role = EXCLUDED.role,
        verified = EXCLUDED.verified,
        banned = EXCLUDED.banned,
        updated_at = now();

DELETE FROM profile_skills
WHERE profile_id IN (
    SELECT p.id
    FROM profiles p
    JOIN users u ON u.id = p.user_id
    WHERE u.email = 'student@kametud.com'
);

INSERT INTO profile_skills (profile_id, skill)
SELECT p.id, skill
FROM profiles p
JOIN users u ON u.id = p.user_id
CROSS JOIN (VALUES ('Programmation'), ('Rédaction académique'), ('Comptabilité')) AS skills(skill)
WHERE u.email = 'student@kametud.com';

INSERT INTO verification_requests (
    id, student_id, student_name, email, university, id_type,
    id_file_url, selfie_url, student_card_url, status, submitted_at, reviewed_at
)
SELECT
    gen_random_uuid(),
    u.id,
    'luc tc',
    'student@kametud.com',
    'Universite de Dschang',
    'CNI',
    '/api/storage/files/demo-id.svg',
    '/api/storage/files/demo-selfie.svg',
    '/api/storage/files/demo-student-card.svg',
    'APPROVED',
    now(),
    now()
FROM users u
WHERE u.email = 'student@kametud.com'
  AND NOT EXISTS (
      SELECT 1
      FROM verification_requests vr
      WHERE vr.student_id = u.id
        AND vr.status = 'APPROVED'
  );
"@

Invoke-IdentitySql $sql

Write-Step "Comptes demo disponibles"

$summarySql = @"
SELECT
    u.email,
    lower(u.role) AS role,
    u.enabled,
    p.verified,
    p.banned,
    concat_ws(' ', p.first_name, p.last_name) AS nom
FROM users u
JOIN profiles p ON p.user_id = u.id
WHERE u.email IN ('admin@kametud.com', 'moderator@kametud.com', 'student@kametud.com', 'client@kametud.com')
ORDER BY
    CASE u.email
        WHEN 'admin@kametud.com' THEN 1
        WHEN 'moderator@kametud.com' THEN 2
        WHEN 'student@kametud.com' THEN 3
        WHEN 'client@kametud.com' THEN 4
        ELSE 5
    END;
"@

Invoke-IdentitySql $summarySql

Write-Success "Seed termine. Mot de passe commun : $PasswordLabel"
Write-Host ""
Write-Host "Identifiants :" -ForegroundColor Yellow
Write-Host "  admin@kametud.com     / $PasswordLabel"
Write-Host "  moderator@kametud.com / $PasswordLabel"
Write-Host "  student@kametud.com   / $PasswordLabel"
Write-Host "  client@kametud.com    / $PasswordLabel"
