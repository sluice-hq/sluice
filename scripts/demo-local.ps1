[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://localhost:8080/api/v1',
    [string]$CallbackUrl = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $CallbackUrl) {
    $CallbackUrl = 'http://localhost:8787/webhooks'
    Push-Location $repoRoot
    try {
        $apiContainer = & docker compose ps -q api 2>$null
        if ($LASTEXITCODE -eq 0 -and $apiContainer) {
            $CallbackUrl = 'http://host.docker.internal:8787/webhooks'
        }
    } finally { Pop-Location }
}
$previousBase = $env:SLUICE_API_BASE_URL
$previousCallback = $env:SLUICE_DEMO_CALLBACK_URL
try {
    $env:SLUICE_API_BASE_URL = $ApiBaseUrl
    $env:SLUICE_DEMO_CALLBACK_URL = $CallbackUrl
    & node (Join-Path $repoRoot 'examples/marketplace/demo.ts')
    if ($LASTEXITCODE -ne 0) { throw 'The API reference integration failed. Inspect the error above.' }
} finally {
    $env:SLUICE_API_BASE_URL = $previousBase
    $env:SLUICE_DEMO_CALLBACK_URL = $previousCallback
}
