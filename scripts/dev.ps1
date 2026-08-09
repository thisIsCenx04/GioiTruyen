[CmdletBinding()]
param()

$projectRoot = $PSScriptRoot | Split-Path -Parent
$backendScript = Join-Path $projectRoot "be\run-local-backend.ps1"
$frontendDir = Join-Path $projectRoot "fe\apps\web"

Write-Host "[INFO] Starting backend with Maven..." -ForegroundColor Cyan
$backend = Start-Process powershell `
    -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $backendScript, "-Mode", "local" `
    -PassThru

Write-Host "[INFO] Starting frontend with npm..." -ForegroundColor Cyan
Push-Location $frontendDir
npm run dev
$frontendExitCode = $LASTEXITCODE
Pop-Location

if (-not $backend.HasExited) {
    Stop-Process -Id $backend.Id
}

exit $frontendExitCode
