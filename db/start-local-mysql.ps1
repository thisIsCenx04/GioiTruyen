[CmdletBinding()]
param(
    # Overrides the port discovered from .env / the application default.
    [int]$Port = 0
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$mysqlHome = Join-Path $workspaceRoot ".local-tools\mysql-8.4.9-winx64"
$mysqlData = Join-Path $workspaceRoot ".local-data\mysql"
$mysqlAdmin = Join-Path $mysqlHome "bin\mysqladmin.exe"
$mysqlServer = Join-Path $mysqlHome "bin\mysqld.exe"

# The port has to come from the same place the backend reads it, otherwise this
# script can happily start a server on 3307 while Spring connects to 3306 and
# reports an empty database.
if ($Port -le 0) {
    $envFile = Join-Path $projectRoot ".env"
    if (Test-Path -LiteralPath $envFile) {
        $urlLine = Select-String -LiteralPath $envFile -Pattern '^\s*MYSQL_URL\s*=' | Select-Object -First 1
        if ($urlLine -and $urlLine.Line -match ':(\d+)/') {
            $Port = [int]$Matches[1]
        }
    }
}
if ($Port -le 0) { $Port = 3307 }

Write-Host "Local MySQL target: 127.0.0.1:$Port (from .env MYSQL_URL)."

# Anything already listening is the database the backend will use - a developer
# running MySQL as a Windows service is the normal case on this project.
$listening = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
if ($listening.Count -gt 0) {
    Write-Host "MySQL is already listening on 127.0.0.1:$Port. Nothing to start."
    return
}

# No server on the port: fall back to the portable build, if it was set up.
# This is a convenience, not a requirement, so a missing copy must not abort
# the caller (run-local-backend.ps1 dot-invokes this script).
if (-not (Test-Path -LiteralPath $mysqlServer)) {
    Write-Warning "Nothing is listening on 127.0.0.1:$Port and no portable MySQL was found at $mysqlHome."
    Write-Warning "Start your MySQL service, or point MYSQL_URL in .env at a running instance."
    return
}

$arguments = @(
    "--basedir=$mysqlHome"
    "--datadir=$mysqlData"
    "--port=$Port"
    "--bind-address=127.0.0.1"
    "--log-error=$(Join-Path $mysqlData 'mysql-error.log')"
    "--pid-file=$(Join-Path $mysqlData 'mysql.pid')"
)

Start-Process `
    -FilePath $mysqlServer `
    -ArgumentList $arguments `
    -WindowStyle Hidden | Out-Null

for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Milliseconds 500
    & $mysqlAdmin --host=127.0.0.1 --port=$Port --user=root ping *> $null
    if ($LASTEXITCODE -eq 0) {
        $initSql = Join-Path $PSScriptRoot "init-permissions.sql"
        if (Test-Path $initSql) {
            Get-Content $initSql | & "$mysqlHome\bin\mysql.exe" --host=127.0.0.1 --port=$Port --user=root *> $null
        }
        Write-Host "Local MySQL started on 127.0.0.1:$Port."
        return
    }
}

Write-Warning "Portable MySQL did not become ready on port $Port. Check $mysqlData\mysql-error.log."
