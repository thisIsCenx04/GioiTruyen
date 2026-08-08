[CmdletBinding()]
param()

$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$mysqlHome = Join-Path $workspaceRoot ".local-tools\mysql-8.4.9-winx64"
$mysqlData = Join-Path $workspaceRoot ".local-data\mysql"
$mysqlAdmin = Join-Path $mysqlHome "bin\mysqladmin.exe"
$mysqlServer = Join-Path $mysqlHome "bin\mysqld.exe"

if (-not (Test-Path -LiteralPath $mysqlServer)) {
    throw "Local MySQL was not found at $mysqlHome."
}

& $mysqlAdmin --host=127.0.0.1 --port=3307 --user=root ping *> $null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Local MySQL is already running on 127.0.0.1:3307."
    return
}

$arguments = @(
    "--basedir=$mysqlHome"
    "--datadir=$mysqlData"
    "--port=3307"
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
    & $mysqlAdmin --host=127.0.0.1 --port=3307 --user=root ping *> $null
    if ($LASTEXITCODE -eq 0) {
        $initSql = Join-Path $PSScriptRoot "init-permissions.sql"
        if (Test-Path $initSql) {
            Get-Content $initSql | & "$mysqlHome\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root *> $null
        }
        Write-Host "Local MySQL started on 127.0.0.1:3307."
        return
    }
}

throw "Local MySQL did not become ready. Check $mysqlData\mysql-error.log."
