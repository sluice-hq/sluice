[CmdletBinding()]
param([switch]$SkipInstall)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $repoRoot '.sluice'
$logDir = Join-Path $runtimeDir 'logs'
$runDir = Join-Path $runtimeDir 'run'

function Wait-Http([string]$Uri, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

New-Item -ItemType Directory -Force -Path $logDir, $runDir | Out-Null

docker version | Out-Null
Push-Location $repoRoot
try {
    docker compose up -d --wait postgres rabbitmq azurite prometheus grafana
} finally {
    Pop-Location
}

if (-not (Wait-Http 'http://localhost:8080/actuator/health' 2)) {
    $backendDir = Join-Path $repoRoot 'backend'
    $backend = Start-Process -FilePath (Join-Path $backendDir 'gradlew.bat') `
        -ArgumentList 'bootRun', '--console=plain' -WorkingDirectory $backendDir -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDir 'backend.out.log') `
        -RedirectStandardError (Join-Path $logDir 'backend.err.log')
    Set-Content -LiteralPath (Join-Path $runDir 'backend.pid') -Value $backend.Id
}

$frontendDir = Join-Path $repoRoot 'frontend'
if (-not $SkipInstall -and -not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Push-Location $frontendDir
    try { npm ci } finally { Pop-Location }
}

if (-not (Wait-Http 'http://localhost:3000/login' 2)) {
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    $frontend = Start-Process -FilePath $npm -ArgumentList 'run', 'dev' -WorkingDirectory $frontendDir `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'frontend.out.log') `
        -RedirectStandardError (Join-Path $logDir 'frontend.err.log')
    Set-Content -LiteralPath (Join-Path $runDir 'frontend.pid') -Value $frontend.Id
}

if (-not (Wait-Http 'http://localhost:8080/actuator/health' 180)) {
    Get-Content -LiteralPath (Join-Path $logDir 'backend.err.log') -Tail 40 -ErrorAction SilentlyContinue
    throw 'The backend did not become ready. See .sluice/logs/backend.*.log.'
}
if (-not (Wait-Http 'http://localhost:3000/login' 120)) {
    Get-Content -LiteralPath (Join-Path $logDir 'frontend.err.log') -Tail 40 -ErrorAction SilentlyContinue
    throw 'The frontend did not become ready. See .sluice/logs/frontend.*.log.'
}

Write-Host 'Sluice is ready:'
Write-Host '  Dashboard  http://localhost:3000/signup'
Write-Host '  API        http://localhost:8080/api/v1'
Write-Host '  OpenAPI    http://localhost:8080/api/v1/openapi.json'
Write-Host '  Grafana    http://localhost:3001'
Write-Host 'Run .\scripts\demo-local.ps1 for the API golden path.'
