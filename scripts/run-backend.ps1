<#
.SYNOPSIS
Starts the Spring Boot backend with project-local environment loaded.

.DESCRIPTION
Loads root .env into THIS PowerShell process via dev-env.ps1, then runs Maven
from backend\ (which uses .mvn\maven.config -> E:\CivicSense\tools\maven-repo).
No system or user environment variables are changed.
#>

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

. (Join-Path $PSScriptRoot 'dev-env.ps1')

Push-Location (Join-Path $repoRoot 'backend')
try {
    & mvn -B spring-boot:run
} finally {
    Pop-Location
}
