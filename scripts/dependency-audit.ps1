param(
  [string]$Maven = 'mvn',
  [string]$Npm = 'npm'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$osvBatchEndpoint = 'https://api.osv.dev/v1/querybatch'
$osvVulnEndpoint = 'https://api.osv.dev/v1/vulns'

function Write-Step([string]$message) {
  Write-Host "`n== $message ==" -ForegroundColor Cyan
}

function Get-Cvss3Severity([string]$vector) {
  if ($vector -notmatch 'AV:([NALP])/AC:([LH])/PR:([NLH])/UI:([NR])/S:([UC])/C:([HLN])/I:([HLN])/A:([HLN])') {
    return 'UNKNOWN'
  }
  $av = @{ N = 0.85; A = 0.62; L = 0.55; P = 0.2 }[$Matches[1]]
  $ac = @{ L = 0.77; H = 0.44 }[$Matches[2]]
  $scope = $Matches[5]
  $prKey = $Matches[3]
  if ($scope -eq 'U') {
    $pr = @{ N = 0.85; L = 0.62; H = 0.27 }[$prKey]
  } else {
    $pr = @{ N = 0.85; L = 0.68; H = 0.5 }[$prKey]
  }
  $ui = @{ N = 0.85; R = 0.62 }[$Matches[4]]
  $cia = @{ H = 0.56; L = 0.22; N = 0 }
  $confidentiality = $cia[$Matches[6]]
  $integrity = $cia[$Matches[7]]
  $availability = $cia[$Matches[8]]

  $impactSubScore = 1 - ((1 - $confidentiality) * (1 - $integrity) * (1 - $availability))
  if ($scope -eq 'U') {
    $impact = 6.42 * $impactSubScore
  } else {
    $impact = 7.52 * ($impactSubScore - 0.029) - 3.25 * [Math]::Pow($impactSubScore - 0.02, 15)
  }
  $exploitability = 8.22 * $av * $ac * $pr * $ui
  if ($impact -le 0) { return 'NONE' }
  if ($scope -eq 'U') {
    $base = [Math]::Min($impact + $exploitability, 10)
  } else {
    $base = [Math]::Min(1.08 * ($impact + $exploitability), 10)
  }
  if ($base -ge 9.0) { return 'CRITICAL' }
  if ($base -ge 7.0) { return 'HIGH' }
  if ($base -ge 4.0) { return 'MODERATE' }
  return 'LOW'
}

function Resolve-OsvSeverity($vuln) {
  if ($vuln.database_specific -and $vuln.database_specific.severity) {
    return ([string]$vuln.database_specific.severity).ToUpperInvariant()
  }
  foreach ($entry in @($vuln.severity)) {
    if (-not $entry -or -not $entry.score) { continue }
    $severity = Get-Cvss3Severity ([string]$entry.score)
    if ($severity -ne 'UNKNOWN') { return $severity }
  }
  return 'UNKNOWN'
}

Push-Location $root
try {
  Write-Step 'Frontend dependency audit: npm audit --audit-level=high'
  & $Npm --prefix (Join-Path $root 'frontend') audit --audit-level=high
  if ($LASTEXITCODE -ne 0) {
    throw "npm audit failed with exit code $LASTEXITCODE"
  }

  Write-Step 'Backend dependency inventory: mvn -B dependency:tree -DskipTests'
  $mvnOutput = (& $Maven -B -f (Join-Path $root 'backend/pom.xml') dependency:tree -DskipTests 2>&1 | Out-String)
  if ($LASTEXITCODE -ne 0) {
    Write-Host $mvnOutput
    throw "Maven dependency:tree failed with exit code $LASTEXITCODE"
  }

  $coordinates = @{}
  foreach ($line in ($mvnOutput -split "`r?`n")) {
    if ($line -match '([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):(?:jar|pom|war|test-jar):([A-Za-z0-9_.-]+)') {
      $group = $Matches[1]
      $artifact = $Matches[2]
      $version = $Matches[3]
      if ($group -notlike '*${*' -and $artifact -notlike '*${*' -and $version -notlike '*${*') {
        $coordinates["$group`:$artifact"] = $version
      }
    }
  }
  Write-Host "Discovered $($coordinates.Count) resolved Maven coordinates."

  $queries = @($coordinates.GetEnumerator() | Sort-Object Name | ForEach-Object {
    @{
      package = @{ name = $_.Name; ecosystem = 'Maven' }
      version = $_.Value
    }
  })

  $rawFindings = @()
  for ($offset = 0; $offset -lt $queries.Count; $offset += 100) {
    $end = [Math]::Min($offset + 99, $queries.Count - 1)
    $slice = @($queries[$offset..$end])
    $body = @{ queries = $slice } | ConvertTo-Json -Depth 8
    $response = Invoke-RestMethod -Method Post -Uri $osvBatchEndpoint -ContentType 'application/json' -Body $body
    if (-not $response.results) { continue }

    for ($index = 0; $index -lt $response.results.Count; $index++) {
      foreach ($vuln in @($response.results[$index].vulns)) {
        if (-not $vuln) { continue }
        $rawFindings += [pscustomobject]@{
          Package = $slice[$index].package.name
          Version = $slice[$index].version
          ID      = $vuln.id
        }
      }
    }
  }

  $details = @{}
  foreach ($id in @($rawFindings.ID | Sort-Object -Unique)) {
    $details[$id] = Invoke-RestMethod -Method Get -Uri "$osvVulnEndpoint/$id"
  }

  $findings = @($rawFindings | ForEach-Object {
    $detail = $details[$_.ID]
    [pscustomobject]@{
      Package  = $_.Package
      Version  = $_.Version
      ID       = $_.ID
      Alias    = (($detail.aliases | Select-Object -First 1) -join ',')
      Severity = Resolve-OsvSeverity $detail
      Summary  = $detail.summary
    }
  })

  Write-Step 'Maven OSV query results'
  if ($findings.Count -eq 0) {
    Write-Host 'No OSV advisories reported for resolved Maven dependencies.'
  } else {
    $findings | Sort-Object @{ Expression = {
        switch ($_.Severity) {
          'CRITICAL' { 0 }
          'HIGH' { 1 }
          'MODERATE' { 2 }
          'LOW' { 3 }
          default { 4 }
        }
      }; Ascending = $true }, Package | Format-Table -Wrap -AutoSize
  }

  $blocking = @($findings | Where-Object { $_.Severity -in @('HIGH', 'CRITICAL') })
  if ($blocking.Count -gt 0) {
    throw "$($blocking.Count) high/critical Maven advisory finding(s) require action."
  }

  Write-Host "`nDependency audit passed: no high/critical findings." -ForegroundColor Green
} finally {
  Pop-Location
}
