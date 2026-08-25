[CmdletBinding()]
param([string]$ApiBaseUrl = 'http://localhost:8080/api/v1')

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $repoRoot '.sluice'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "demo-$suffix@example.com"
$password = 'demo-password-2026'
$signupBody = @{ email = $email; password = $password; projectName = "Demo $suffix" } | ConvertTo-Json
$signup = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/signup" -ContentType 'application/json' -Body $signupBody
$humanHeaders = @{ Authorization = "Bearer $($signup.token)"; 'X-Project-ID' = $signup.selectedProjectId }

$keyBody = @{ name = 'local-demo' } | ConvertTo-Json
$createdKey = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/projects/$($signup.selectedProjectId)/api-keys" `
    -Headers $humanHeaders -ContentType 'application/json' -Body $keyBody
$apiHeaders = @{ 'X-API-Key' = $createdKey.value }

$demoDirectory = Join-Path $repoRoot 'demo'
$definition = Get-Content -LiteralPath (Join-Path $demoDirectory 'pipeline.json') -Raw | ConvertFrom-Json
$pipelineBody = @{ name = 'Demo WebP governance'; description = 'Local golden-path fixture'; definition = $definition } | ConvertTo-Json -Depth 30
$pipeline = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/pipelines" -Headers $apiHeaders -ContentType 'application/json' -Body $pipelineBody
$publishBody = @{ revision = $pipeline.draft.revision } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/pipelines/$($definition.slug)/publish" -Headers $apiHeaders `
    -ContentType 'application/json' -Body $publishBody | Out-Null

$inputPath = Join-Path $runtimeDir 'demo-input.png'
$inputBase64 = (Get-Content -LiteralPath (Join-Path $demoDirectory 'sample.png.base64') -Raw).Trim()
[IO.File]::WriteAllBytes($inputPath, [Convert]::FromBase64String($inputBase64))
$inputSize = (Get-Item -LiteralPath $inputPath).Length
$uploadBody = @{ filename = 'demo-input.png'; contentType = 'image/png'; size = $inputSize } | ConvertTo-Json
$upload = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/uploads" -Headers $apiHeaders -ContentType 'application/json' -Body $uploadBody
Invoke-WebRequest -UseBasicParsing -Method Put -Uri $upload.uploadUrl -InFile $inputPath `
    -Headers @{ 'x-ms-blob-type' = 'BlockBlob'; 'Content-Type' = 'image/png' } | Out-Null
Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/uploads/$($upload.assetId)/complete" `
    -Headers ($apiHeaders + @{ 'Idempotency-Key' = "upload-$suffix" }) | Out-Null

$runBody = @{ pipeline = $definition.slug; alias = 'stable'; inputAssetId = $upload.assetId } | ConvertTo-Json
$run = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/runs" -Headers ($apiHeaders + @{ 'Idempotency-Key' = "run-$suffix" }) `
    -ContentType 'application/json' -Body $runBody
$deadline = (Get-Date).AddSeconds(120)
do {
    Start-Sleep -Seconds 1
    $run = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/runs/$($run.id)" -Headers $apiHeaders
} while ($run.status -in @('QUEUED', 'RUNNING', 'RETRY_WAIT') -and (Get-Date) -lt $deadline)

if ($run.status -ne 'COMPLETED') {
    throw "Demo run ended as $($run.status): $($run.error.code) $($run.error.message)"
}
if ($run.outputs.Count -ne 1 -or $run.outputs[0].contentType -ne 'image/webp') {
    throw 'The demo did not produce exactly one declared WebP output.'
}

$download = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/assets/$($run.outputs[0].id)/download" -Headers $apiHeaders
$outputPath = Join-Path $runtimeDir 'demo-output.webp'
Invoke-WebRequest -UseBasicParsing -Uri $download.downloadUrl -OutFile $outputPath | Out-Null
if ((Get-Item -LiteralPath $outputPath).Length -lt 1) { throw 'The downloaded output is empty.' }

[ordered]@{
    result = 'PASS'
    account = $email
    projectId = $signup.selectedProjectId
    pipeline = "$($definition.slug)@1"
    runId = $run.id
    status = $run.status
    governance = $run.governance.decision
    outputContentType = $run.outputs[0].contentType
    outputBytes = $run.outputs[0].size
    outputFile = $outputPath
} | ConvertTo-Json
