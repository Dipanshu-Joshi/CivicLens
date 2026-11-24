<#
.SYNOPSIS
Loads CivicSense local environment into the CURRENT PowerShell session only.

.DESCRIPTION
- Reads the git-ignored root .env file.
- Exports each key as a process-scoped environment variable (no user/machine changes).
- Prepends the portable Node.js directory to PATH for this session only.
- Prints only KEY names and masked values; secrets are never echoed.

USAGE
    .\scripts\dev-env.ps1
    npm --version        # resolves to the portable Node/npm in tools\node
#>

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'
$nodeDir = Join-Path $repoRoot 'tools\node\node-v26.13.1-win-x64'

if (-not (Test-Path $nodeDir)) {
    throw "Portable Node.js not found at $nodeDir"
}

# Session-only PATH change — nothing is written to the registry or user profile.
$env:PATH = "$nodeDir;$env:PATH"

if (-not (Test-Path $envFile)) {
    Write-Warning "No .env found at $envFile. Copy .env.example to .env and fill local values before database tests."
} else {
    $secretPattern = 'PASSWORD|SECRET|KEY|TOKEN|CREDENTIAL'
    $loaded = 0
    foreach ($raw in Get-Content -LiteralPath $envFile) {
        $line = $raw.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "Env:$key" -Value $value
        $loaded++
        if ($key -match $secretPattern) {
            Write-Host ("  {0} = ****" -f $key)
        }
    }
    Write-Host "Loaded $loaded variables from .env (process scope only)."
}

Write-Host "Portable Node: $nodeDir"
Write-Host "node $($env:PATH -split ';' | Select-Object -First 1)"
