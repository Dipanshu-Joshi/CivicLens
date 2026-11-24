<#
.SYNOPSIS
    Local no-Docker backend/SPA smoke and restart check for Phase 12.

.DESCRIPTION
    Starts the packaged Spring Boot jar with the existing H2 TEST PROFILE, the
    test-scope H2 jar injected through Spring Boot's PropertiesLauncher, and the
    test schema. It checks the health endpoint and a correlated response, starts
    the built Vite SPA, checks the root document, stops/restarts the backend, and
    cleans up all child processes. This is LOCAL EVIDENCE ONLY: it does not verify
    hosted Supabase/PostGIS, S3, SMTP, Flyway on PostgreSQL, or hosted health.

USAGE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase12-local-smoke.ps1
#>

param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
  [string]$Java = 'java',
  [string]$Node = (Join-Path $Root 'tools\node\node-v26.13.1-win-x64\node.exe'),
  [int]$BackendPort = 18080,
  [int]$FrontendPort = 4173
)

$ErrorActionPreference = 'Stop'

$jar = Get-ChildItem -LiteralPath (Join-Path $Root 'backend\target') -Filter 'civicsense-backend-*.jar' -File |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'No packaged backend jar found. Run mvn -B package first.' }

$schema = Join-Path $Root 'backend\src\test\resources\schema-test.sql'
if (-not (Test-Path -LiteralPath $schema)) { throw "H2 test schema not found: $schema" }
$h2Jar = Get-ChildItem -Path (Join-Path $Root 'tools\maven-repo\com\h2database\h2') `
  -Filter 'h2-*.jar' -Recurse -File | Sort-Object Name -Descending | Select-Object -First 1
if (-not $h2Jar) { throw 'No H2 test jar found in the project-local Maven repository.' }
if (-not (Test-Path -LiteralPath (Join-Path $Root 'frontend\dist\index.html'))) {
  throw 'Frontend dist/index.html not found. Run npm run build first.'
}
if (-not (Test-Path -LiteralPath $Node)) { throw "Portable Node.js not found: $Node" }

$evidence = Join-Path ([IO.Path]::GetTempPath()) 'civicsense-phase12-local-smoke'
if (Test-Path -LiteralPath $evidence) { Remove-Item -LiteralPath $evidence -Recurse -Force }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null

$backend = $null
$frontend = $null
$schemaArg = '--spring.sql.init.schema-locations=file:///' + ($schema -replace '\\', '/')

function Start-Backend {
  $stdout = Join-Path $evidence "backend-$([guid]::NewGuid().ToString('N')).out.log"
  $stderr = Join-Path $evidence "backend-$([guid]::NewGuid().ToString('N')).err.log"
  $arguments = @(
    "-Dloader.path=$($h2Jar.FullName)",
    '-cp', $jar.FullName,
    'org.springframework.boot.loader.launch.PropertiesLauncher',
    '--spring.profiles.active=test',
    "--server.port=$BackendPort",
    $schemaArg
  )
  return Start-Process -FilePath $Java -ArgumentList $arguments -PassThru `
    -WorkingDirectory (Join-Path $Root 'backend') `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr
}

function Wait-ForResponse([string]$url, [int[]]$acceptedStatuses, [int]$timeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  $last = ''
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
      if ($acceptedStatuses -contains [int]$response.StatusCode) { return $response }
      $last = "HTTP $([int]$response.StatusCode)"
    } catch {
      if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
        if ($acceptedStatuses -contains $status) {
          return [pscustomobject]@{
            StatusCode = $status
            Headers = $_.Exception.Response.Headers
            Content = ''
          }
        }
        $last = "HTTP $status"
      } else {
        $last = $_.Exception.Message
      }
    }
    Start-Sleep -Milliseconds 500
  }
  throw "No accepted response from $url. Last result: $last"
}

function Stop-Tree([System.Diagnostics.Process]$process) {
  if (-not $process -or $process.HasExited) { return }
  Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
  Start-Sleep -Milliseconds 500
  if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
}

try {
  Write-Host '== LOCAL SMOKE: packaged backend + built SPA (H2 test double) ==' -ForegroundColor Cyan

  $backend = Start-Backend
  $healthUrl = "http://127.0.0.1:$BackendPort/api/v1/meta/health"
  # 503 is expected locally because postgis/objectStorage are test doubles.
  $health = Wait-ForResponse $healthUrl @(200, 503) 90
  if (-not $health.Headers['X-Correlation-Id']) { throw 'Health response did not expose X-Correlation-Id.' }
  Write-Host ("-- backend health reached: HTTP {0}; correlation header present" -f [int]$health.StatusCode)

  $actuator = Wait-ForResponse "http://127.0.0.1:$BackendPort/api/v1/actuator/health" @(200, 503) 15
  Write-Host ("-- actuator health reached: HTTP {0}" -f [int]$actuator.StatusCode)

  $vite = Join-Path $Root 'frontend\node_modules\vite\bin\vite.js'
  $frontend = Start-Process -FilePath $Node `
    -ArgumentList @($vite, 'preview', '--host', '127.0.0.1', '--port', "$FrontendPort", '--strictPort') `
    -PassThru -WorkingDirectory (Join-Path $Root 'frontend') `
    -RedirectStandardOutput (Join-Path $evidence 'frontend.out.log') `
    -RedirectStandardError (Join-Path $evidence 'frontend.err.log')

  $spa = Wait-ForResponse "http://127.0.0.1:$FrontendPort/" @(200) 60
  if ($spa.Content -notmatch '<div id="root">') { throw 'SPA root document did not contain the React mount point.' }
  Write-Host '-- SPA root document served with React mount point'

  Write-Host '-- restarting backend process...'
  Stop-Tree $backend
  $backend = Start-Backend
  $restarted = Wait-ForResponse $healthUrl @(200, 503) 90
  if (-not $restarted.Headers['X-Correlation-Id']) { throw 'Restarted health response lost X-Correlation-Id.' }
  Write-Host ("-- backend restart recovered: HTTP {0}; correlation header present" -f [int]$restarted.StatusCode)

  Write-Host "`nLOCAL SMOKE PASSED: health, actuator, SPA and restart recovery are reachable." -ForegroundColor Green
  Write-Host 'Scope: H2 test double + built SPA only. Hosted services were NOT contacted.' -ForegroundColor Yellow
  Write-Host "Logs: $evidence" -ForegroundColor Yellow
} finally {
  Stop-Tree $frontend
  Stop-Tree $backend
}
