param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
Set-Location $Root

$files = @(& git ls-files | Where-Object { $_ -and $_ -notmatch '\.env\.example$' })
$binaryExtensions = @('.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.pdf', '.zip', '.jar', '.woff', '.woff2', '.ttf')
$patterns = @(
  'AKIA[0-9A-Z]{16}',
  'ASIA[0-9A-Z]{16}',
  'gh[pousr]_[A-Za-z0-9]{36,}',
  'xox[baprs]-[A-Za-z0-9-]{10,}',
  '-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
  '(?i)(api[_-]?key|secret[_-]?key|access[_-]?token|password)\s*[:=]\s*["'']?[A-Za-z0-9_\-/+]{20,}["'']?'
)

$findings = @()
$accidentalFiles = @($files | Where-Object {
    ($_ -match '(^|/)\.env($|\.(?!example$))') -or
    ($_ -match '(^|/)(secrets?|credentials?)(/|\.|$)') -or
    ($binaryExtensions -contains [IO.Path]::GetExtension($_).ToLowerInvariant() -and $_ -match '( secret| credential| \.key$)')
  })

foreach ($file in $accidentalFiles) {
  $findings += [pscustomobject]@{ File = $file; Line = 0; Pattern = 'tracked secret filename' }
}

foreach ($file in $files) {
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
  if ($binaryExtensions -contains [IO.Path]::GetExtension($file).ToLowerInvariant()) { continue }

  foreach ($match in @(Select-String -LiteralPath $file -Pattern $patterns -AllMatches)) {
    $findings += [pscustomobject]@{
      File    = $file
      Line    = $match.LineNumber
      Pattern = $match.Pattern
    }
  }
}

Write-Host '== Secret scan: tracked files =='
Write-Host "Scanned $($files.Count) tracked files."
if ($findings.Count -eq 0) {
  Write-Host 'No credential-like patterns or accidental secret files found.' -ForegroundColor Green
  exit 0
}

$findings | Sort-Object File, Line | Format-Table -AutoSize
Write-Error "Secret scan found $($findings.Count) potential issue(s); values are intentionally redacted."
exit 1
