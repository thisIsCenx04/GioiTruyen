[CmdletBinding()]
param()

$projectRoot = Split-Path -Parent $PSScriptRoot
$webAppDir = Join-Path $projectRoot "frontend\apps\web"
$preferredPort = if ($env:FE_PORT) { [int]$env:FE_PORT } else { 3000 }

function Test-HttpOk([string] $url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Test-PortFree([int] $port) {
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    return $listeners.Count -eq 0
}

$homeUrl = "http://127.0.0.1:$preferredPort/"
$listeners = @(Get-NetTCPConnection -LocalPort $preferredPort -State Listen -ErrorAction SilentlyContinue)

if ($listeners.Count -gt 0) {
    if (Test-HttpOk $homeUrl) {
        $pids = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ", "
        Write-Host "Frontend client is already running at $homeUrl (PID: $pids)."
        exit 0
    }

    $fallbackPort = $preferredPort + 1
    while (-not (Test-PortFree $fallbackPort)) {
        $fallbackPort += 1
    }
    Write-Host "Port $preferredPort is busy but did not return HTTP 200. Starting frontend on http://127.0.0.1:$fallbackPort/."
    Set-Location $webAppDir
    pnpm exec next dev --port $fallbackPort
    exit $LASTEXITCODE
}

Write-Host "Starting frontend client at $homeUrl."
Set-Location $webAppDir
pnpm exec next dev --port $preferredPort
exit $LASTEXITCODE
