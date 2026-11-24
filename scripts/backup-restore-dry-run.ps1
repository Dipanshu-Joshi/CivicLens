<#
.SYNOPSIS
    Repeatable LOCAL backup/restore dry run for the documented procedure shape.

.DESCRIPTION
    This script is Phase 11 verification evidence only. It uses the H2 test
    double already present in the project-local Maven repository to prove that
    the documented "schema -> seed -> dump -> restore -> verify" sequence is
    ordered and repeatable.

    It does NOT test hosted Supabase, PostgreSQL/PostGIS, pg_dump/psql, or the
    S3 bucket. See docs/DEPLOYMENT.md for the real operator procedure.

USAGE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-restore-dry-run.ps1
#>

param(
  [string]$WorkRoot = (Join-Path ([IO.Path]::GetTempPath()) 'civicsense-backup-restore-dryrun')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$schema = Join-Path $repoRoot 'backend\src\test\resources\schema-test.sql'

# Prefer the newest H2 jar available in the project-local Maven repository.
$jar = Get-ChildItem -Path (Join-Path $repoRoot 'tools\maven-repo\com\h2database\h2') `
  -Filter 'h2-*.jar' -Recurse -File -ErrorAction SilentlyContinue |
  Sort-Object { [version]($_.Directory.Name) } -Descending |
  Select-Object -First 1

if (-not $jar) {
  throw 'No H2 jar found under tools\maven-repo\com\h2database\h2.'
}
if (-not (Test-Path $schema)) {
  throw "H2 test schema not found at $schema"
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  throw 'java not found on PATH.'
}

Write-Host '== LOCAL DRY RUN: H2 test double (NOT hosted Supabase) ==' -ForegroundColor Cyan

if (Test-Path $WorkRoot) { Remove-Item -LiteralPath $WorkRoot -Recurse -Force }
New-Item -ItemType Directory -Path $WorkRoot -Force | Out-Null

$liveBase = Join-Path $WorkRoot 'civicsense-live'
$restoreBase = Join-Path $WorkRoot 'civicsense-restored'
$backup = Join-Path $WorkRoot 'civicsense-backup.zip'
$seedSql = Join-Path $WorkRoot '01-seed.sql'
$verifySql = Join-Path $WorkRoot '02-verify.sql'

function Get-H2Url([string]$base) {
  'jdbc:h2:file:' + ($base -replace '\\', '/') + ';MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE'
}

$liveUrl = Get-H2Url $liveBase
$restoreUrl = Get-H2Url $restoreBase

@'
-- Dry-run-only fixture. No real credentials or production data.
INSERT INTO users (id, email, password_hash, role, account_status, full_name, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'dryrun@example.test', 'dry-run-placeholder',
        'CITIZEN', 'ACTIVE', 'Dry Run Citizen', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO audit_logs (id, actor_user_id, actor_role, action, entity_type, entity_id, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
        'CITIZEN', 'DRY_RUN_SEED', 'DEMO', 'seed-1', CURRENT_TIMESTAMP);
INSERT INTO platform_settings ("key", "value", description, updated_at)
VALUES ('dry.run.marker', 'restore-me', 'dry-run marker row', CURRENT_TIMESTAMP);
'@ | Set-Content -LiteralPath $seedSql -Encoding ascii

@'
SELECT 'users=' || (SELECT COUNT(*) FROM users)
    || '|audit_logs=' || (SELECT COUNT(*) FROM audit_logs)
    || '|settings=' || (SELECT COUNT(*) FROM platform_settings)
    || '|email=' || (SELECT email FROM users WHERE id = '11111111-1111-1111-1111-111111111111') AS snapshot;
'@ | Set-Content -LiteralPath $verifySql -Encoding ascii

function Invoke-H2([string]$Url, [string]$Tool, [string[]]$H2Args) {
  $all = @('-cp', $jar.FullName, ('org.h2.tools.' + $Tool), '-url', $Url, '-user', 'sa') + $H2Args
  $output = & java @all 2>&1 | ForEach-Object { $_.ToString() }
  $output | ForEach-Object { Write-Host "    $_" }
  if ($LASTEXITCODE -ne 0) { throw "H2 command failed: java -cp $($jar.Name) $($all -join ' ')" }
  return ($output -join "`n")
}

Write-Host "`n-- 1. Apply H2 test schema (stand-in for Flyway V1-V13) --"
Invoke-H2 $liveUrl 'RunScript' @('-script', $schema) | Out-Null

Write-Host '-- 2. Seed a deterministic fixture --'
Invoke-H2 $liveUrl 'RunScript' @('-script', $seedSql) | Out-Null

Write-Host '-- 3. Capture BEFORE snapshot --'
function Get-Snapshot([string]$raw) {
  (($raw -split "`n") | Where-Object { $_ -match '^users=' }) -join ''
}

$before = Get-Snapshot (Invoke-H2 $liveUrl 'Shell' @('-sql', (Get-Content -LiteralPath $verifySql -Raw)))
Write-Host "--    Snapshot: $before"

Write-Host '-- 4. BACKUP TO a zip archive --'
Invoke-H2 $liveUrl 'Shell' @('-sql', "BACKUP TO '$($backup -replace '\\', '/')'") | Out-Null

if (-not (Test-Path $backup) -or (Get-Item $backup).Length -eq 0) {
  throw 'Backup archive was not created.'
}
Write-Host "--    Archive: $backup ($((Get-Item $backup).Length) bytes)"

Write-Host '-- 5. Simulate loss of the live database file --'
Get-ChildItem -LiteralPath $WorkRoot -Filter 'civicsense-live.*' -File | Remove-Item -Force

Write-Host '-- 6. RESTORE FROM the archive into a fresh disposable target --'
$restoreArgs = @('-cp', $jar.FullName, 'org.h2.tools.Restore',
                 '-file', $backup, '-dir', $WorkRoot, '-db', 'civicsense-restored')
$restoreOutput = & java @restoreArgs 2>&1 | ForEach-Object { $_.ToString() }
$restoreOutput | ForEach-Object { Write-Host "    $_" }
if ($LASTEXITCODE -ne 0) { throw 'H2 Restore tool failed.' }
if (-not (Test-Path "$restoreBase.mv.db")) { throw 'Restored database file was not created.' }

Write-Host '-- 7. Capture AFTER snapshot from restored target --'
$after = Get-Snapshot (Invoke-H2 $restoreUrl 'Shell' @('-sql', (Get-Content -LiteralPath $verifySql -Raw)))
Write-Host "--    Snapshot: $after"

if ($before -ne $after) {
  Write-Error "Snapshot mismatch.`nBefore: $before`nAfter:  $after"
  exit 1
}

Write-Host "`nDRY RUN PASSED: schema + seed survived backup -> loss -> restore." -ForegroundColor Green
Write-Host 'Scope: H2 test double only. Hosted Supabase backup/restore remains unverified.' -ForegroundColor Yellow
Write-Host "Evidence retained at: $WorkRoot"
