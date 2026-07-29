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

& (Join-Path $projectRoot "backend\gradlew.bat") `
    --project-dir (Join-Path $projectRoot "backend") `
    bootRun

exit $LASTEXITCODE
