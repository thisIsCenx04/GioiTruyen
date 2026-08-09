[CmdletBinding()]
param(
    [ValidateSet("local", "production")]
    [string]$Mode = "local"
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$javaHome = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"

if (-not (Test-Path -LiteralPath (Join-Path $javaHome "bin\java.exe"))) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        $javaHome = $env:JAVA_HOME
    } else {
        Write-Host "JDK 21 not found at $javaHome. Using system default java."
    }
} else {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
}

# Select environment file based on mode parameter
$envFileName = if ($Mode -eq "production") { ".env.production" } else { ".env" }
$envFile = Join-Path $projectRoot $envFileName

if (Test-Path $envFile) {
    Write-Host "Loading environment variables from $envFileName (Mode: $Mode)..."
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            if ($key) {
                [System.Environment]::SetEnvironmentVariable($key, $val, [System.EnvironmentVariableTarget]::Process)
            }
        }
    }
} else {
    Write-Host "Warning: Environment file $envFileName not found at $projectRoot"
}

if ($Mode -eq "local") {
    $mysqlScript = Join-Path $projectRoot "db\start-local-mysql.ps1"
    if (Test-Path $mysqlScript) {
        & $mysqlScript
    }
}

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
        Write-Host "Port $apiPort is already in use by PID(s): $pids."
    }
}

Write-Host "`n[INFO] Starting Backend Spring Boot ($Mode mode)..." -ForegroundColor Cyan
Write-Host "[INFO] Server will display '>>> BACKEND SPRING BOOT HAS STARTED SUCCESSFULLY! <<<' when ready." -ForegroundColor Green
Push-Location $PSScriptRoot
mvn spring-boot:run
$exitCode = $LASTEXITCODE
Pop-Location

exit $exitCode
