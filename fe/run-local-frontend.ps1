[CmdletBinding()]
param()

$webAppDir = Join-Path $PSScriptRoot "apps\web"
$preferredPort = if ($env:FE_PORT) { [int]$env:FE_PORT } else { 3000 }

function Test-HttpOk([string] $url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

$homeUrl = "http://127.0.0.1:$preferredPort/"
$listeners = @(Get-NetTCPConnection -LocalPort $preferredPort -State Listen -ErrorAction SilentlyContinue)

if ($listeners.Count -gt 0) {
    if (Test-HttpOk $homeUrl) {
        $pids = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ", "
        Write-Host "Frontend React SPA is already running at $homeUrl (PID: $pids)."
        exit 0
    }
}

Write-Host "Starting Frontend React SPA (Vite) at $homeUrl..."
Set-Location $webAppDir
pnpm dev --port $preferredPort
exit $LASTEXITCODE
