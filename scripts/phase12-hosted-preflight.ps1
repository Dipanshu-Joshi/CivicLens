<#
.SYNOPSIS
    Credential-safe static preflight for hosted Phase 12 verification.

.DESCRIPTION
    Reads the git-ignored root .env and reports which hosted checks can be run.
    It never prints a value. Placeholder-only configuration is reported as
    BLOCKED; it is never treated as successful external verification. Also
    checks local operator CLI availability and the Flyway migration inventory.

    This script performs NO network connection and makes NO claim that hosted
    PostgreSQL/PostGIS, S3, SMTP or deployment has been verified.

USAGE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase12-hosted-preflight.ps1
#>

param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$envFile = Join-Path $Root '.env'
$values = @{}

if (Test-Path -LiteralPath $envFile) {
  foreach ($raw in Get-Content -LiteralPath $envFile) {
    $line = $raw.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    $index = $line.IndexOf('=')
    if ($index -lt 1) { continue }
    $key = $line.Substring(0, $index).Trim()
    $value = $line.Substring($index + 1).Trim()
    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    $values[$key] = $value
  }
}

function Get-Value([string]$key) {
  if ($values.ContainsKey($key)) { return [string]$values[$key] }
  $environmentValue = [Environment]::GetEnvironmentVariable($key)
  return [string]$environmentValue
}

function Is-Configured([string]$key) {
  $value = Get-Value $key
  if ([string]::IsNullOrWhiteSpace($value)) { return $false }
  return $value -notmatch 'PLACEHOLDER|<[^>]+>|SET_NONEMPTY|example\.test|test-only'
}

function Write-Check([string]$name, [bool]$ready, [string[]]$missing) {
  $state = if ($ready) { 'READY' } else { 'BLOCKED' }
  $color = if ($ready) { 'Green' } else { 'Yellow' }
  Write-Host ("[{0}] {1}" -f $state, $name) -ForegroundColor $color
  if ($missing.Count -gt 0) {
    foreach ($item in $missing) { Write-Host ("       missing: {0}" -f $item) -ForegroundColor $color }
  }
}

Write-Host '== Phase 12 hosted preflight (static; no network calls) ==' -ForegroundColor Cyan

$dbMissing = @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD') |
  Where-Object { -not (Is-Configured $_) }
Write-Check 'Hosted Supabase PostgreSQL (Flyway/SELECT 1/Flyway history)' ($dbMissing.Count -eq 0) $dbMissing

$s3Missing = @('CIVICSENSE_S3_ENDPOINT', 'CIVICSENSE_S3_BUCKET', 'CIVICSENSE_S3_ACCESS_KEY', 'CIVICSENSE_S3_SECRET_KEY') |
  Where-Object { -not (Is-Configured $_) }
Write-Check 'Supabase Storage S3 (HeadBucket/secure object checks)' ($s3Missing.Count -eq 0) $s3Missing

$jwtMissing = @('CIVICSENSE_JWT_SECRET') | Where-Object { -not (Is-Configured $_) }
Write-Check 'Production JWT secret' ($jwtMissing.Count -eq 0) $jwtMissing

$mailEnabled = (Get-Value 'CIVICSENSE_MAIL_ENABLED') -eq 'true'
$mailMissing = @('CIVICSENSE_MAIL_HOST', 'CIVICSENSE_MAIL_FROM', 'CIVICSENSE_MAIL_USERNAME', 'CIVICSENSE_MAIL_PASSWORD') |
  Where-Object { -not (Is-Configured $_) }
$mailBlockers = @($mailMissing)
if (-not $mailEnabled) { $mailBlockers += 'CIVICSENSE_MAIL_ENABLED=true' }
Write-Check 'Real SMTP password-reset delivery' ($mailBlockers.Count -eq 0) $mailBlockers

$cliChecks = @(
  @{ Name = 'psql / pg_dump / pg_restore'; Commands = @('psql', 'pg_dump', 'pg_restore') },
  @{ Name = 'aws CLI'; Commands = @('aws') },
  @{ Name = 'redis-cli (only if scale-out is approved)'; Commands = @('redis-cli') }
)
foreach ($check in $cliChecks) {
  $missingTools = @($check.Commands | Where-Object { -not (Get-Command $_ -ErrorAction SilentlyContinue) })
  Write-Check ("Operator CLI: " + $check.Name) ($missingTools.Count -eq 0) $missingTools
}

$migrations = @(Get-ChildItem -LiteralPath (Join-Path $Root 'backend\src\main\resources\db\migration') `
  -Filter 'V*.sql' -File -ErrorAction SilentlyContinue | Sort-Object Name)
$migrationNames = @($migrations.Name)
$expectedNames = @('V1__enable_extensions.sql', 'V2__identity_and_departments.sql',
  'V3__complaints.sql', 'V4__collaboration.sql', 'V5__governance.sql',
  'V6__indexes_and_constraints.sql', 'V7__seed_reference_data.sql',
  'V8__phase5_image_analysis.sql', 'V9__phase6_routing.sql',
  'V10__phase7_department_dashboard.sql', 'V11__phase8_admin_governance.sql',
  'V12__phase9_notification_delivery.sql', 'V13__phase10_password_reset.sql')
$inventoryOk = $migrationNames.Count -eq $expectedNames.Count
foreach ($name in $expectedNames) {
  if ($migrationNames -notcontains $name) { $inventoryOk = $false }
}
Write-Check 'Flyway source inventory (V1–V13; V8__phase5_image_analysis.sql present)' $inventoryOk @()
Write-Host ("       found: {0} migration file(s)" -f $migrations.Count)
foreach ($migration in $migrations) { Write-Host ("       {0}" -f $migration.Name) }

Write-Host "`nPREFLIGHT COMPLETE: hosted verification remains BLOCKED while any item above is BLOCKED." -ForegroundColor Yellow
Write-Host 'No value was printed and no external system was contacted.' -ForegroundColor Yellow
