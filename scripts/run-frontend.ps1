<#
.SYNOPSIS
Starts the Vite dev server with portable Node and project-local environment.

.DESCRIPTION
Loads root .env into THIS PowerShell process via dev-env.ps1, then runs the
portable npm from tools\node. Dependencies stay in frontend\node_modules.
#>

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

. (Join-Path $PSScriptRoot 'dev-env.ps1')

$node = Join-Path $repoRoot 'tools\node\node-v26.13.1-win-x64'
$env:PATH = "$node;$env:PATH"

Push-Location (Join-Path $repoRoot 'frontend')
try {
    & (Join-Path $node 'npm.cmd') run dev
} finally {
    Pop-Location
}
