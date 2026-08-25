[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $repoRoot '.sluice\run'

foreach ($name in @('frontend', 'backend')) {
    $pidFile = Join-Path $runDir "$name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) { continue }
    $processId = [int](Get-Content -LiteralPath $pidFile -Raw)
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        & taskkill.exe /PID $processId /T /F | Out-Null
    }
    Remove-Item -LiteralPath $pidFile -Force
}

Push-Location $repoRoot
try { docker compose down } finally { Pop-Location }
Write-Host 'Sluice stopped. Local Docker volumes were retained.'
