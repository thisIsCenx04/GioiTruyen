[CmdletBinding()]
param()

$projectRoot = Split-Path -Parent $PSScriptRoot
$javaHome = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"

if (-not (Test-Path -LiteralPath (Join-Path $javaHome "bin\java.exe"))) {
    throw "JDK 21 was not found at $javaHome."
}

& (Join-Path $PSScriptRoot "start-local-mysql.ps1")

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

. (Join-Path $PSScriptRoot "load-env.ps1") -Override

$apiPort = if ($env:API_PORT) { [int]$env:API_PORT } else { 8080 }
$apiBaseUrl = if ($env:API_INTERNAL_URL) {
    $env:API_INTERNAL_URL.TrimEnd("/")
} else {
    "http://127.0.0.1:$apiPort/api/v1"
}
$healthUrl = "$apiBaseUrl/actuator/health"
$listeners = @(Get-NetTCPConnection -LocalPort $apiPort -State Listen -ErrorAction SilentlyContinue)

if ($listeners.Count -gt 0) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            $pids = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ", "
            Write-Host "Backend is already running on port $apiPort (PID: $pids). Health check passed."
            exit 0
        }
    } catch {
        $pids = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ", "
        throw "Port $apiPort is already in use by PID(s): $pids, but backend health check failed at $healthUrl. Stop that process or set API_PORT to a free port."
    }
}

& (Join-Path $projectRoot "backend\gradlew.bat") `
    --project-dir (Join-Path $projectRoot "backend") `
    bootRun

exit $LASTEXITCODE
