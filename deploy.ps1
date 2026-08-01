# =====================================================================
# Automated Deployment Script for MonkeyDD / Gioitruyen Platform
# =====================================================================

param (
    [string]$VpsHost = "187.127.214.54",
    [string]$VpsUser = "root",
    [int]$HealthTimeoutSeconds = 60
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "   BAT DAU TRIEN KHAI TU DONG LEN VPS $VpsHost   " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------
# Step 1: Build Backend (Gradle BootJar)
# ---------------------------------------------------------------------
Write-Host "`n[1/5] Building Backend Spring Boot..." -ForegroundColor Yellow
$BackendDir = Join-Path $ScriptDir "backend"
if (-not (Test-Path $BackendDir)) {
    $BackendDir = Join-Path $ScriptDir "..\backend"
}

$GradleBat = Join-Path $BackendDir "gradlew.bat"

if (Test-Path $GradleBat) {
    & $GradleBat --project-dir $BackendDir bootJar
} else {
    Set-Location $BackendDir
    ./gradlew bootJar
}

$JarPath = Join-Path $BackendDir "build\libs\story-platform-backend-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $JarPath)) {
    $JarCandidate = Get-ChildItem -Path (Join-Path $BackendDir "build\libs") -Filter "*.jar" | Select-Object -First 1
    if ($JarCandidate) {
        $JarPath = $JarCandidate.FullName
    } else {
        Write-Error "Backend JAR file not found!"
    }
}
Write-Host "[OK] Backend build success: $(Split-Path $JarPath -Leaf)" -ForegroundColor Green

# ---------------------------------------------------------------------
# Step 2: Build Frontend (Next.js Apps)
# ---------------------------------------------------------------------
Write-Host "`n[2/5] Building Frontend Next.js..." -ForegroundColor Yellow
$WebappDir = $ScriptDir
if (-not (Test-Path (Join-Path $WebappDir "frontend"))) {
    $WebappDir = Join-Path $ScriptDir "Webapp"
}
Set-Location $WebappDir
pnpm build:fe
Write-Host "[OK] Frontend build success!" -ForegroundColor Green

# ---------------------------------------------------------------------
# Step 3: Upload Backend JAR & Frontend Assets to VPS
# ---------------------------------------------------------------------
Write-Host "`n[3/5] Uploading JAR & Frontend Assets to VPS ($VpsHost)..." -ForegroundColor Yellow
Write-Host "Uploading application.jar..." -ForegroundColor Gray
scp -o StrictHostKeyChecking=no "$JarPath" "${VpsUser}@${VpsHost}:/opt/gioitruyen-backend/application.jar"

$FrontendWebDir = Join-Path $WebappDir "frontend\apps\web\.next"
if (Test-Path $FrontendWebDir) {
    Write-Host "Uploading Frontend Web Assets..." -ForegroundColor Gray
    ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "mkdir -p /var/www/gioitruyen-web"
    scp -o StrictHostKeyChecking=no -r "$FrontendWebDir\*" "${VpsUser}@${VpsHost}:/var/www/gioitruyen-web/"
}
Write-Host "[OK] Upload completed!" -ForegroundColor Green

# ---------------------------------------------------------------------
# Step 4: Restart Backend Service on VPS
# ---------------------------------------------------------------------
Write-Host "`n[4/5] Restarting Backend Service on VPS..." -ForegroundColor Yellow
ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "systemctl restart gioitruyen-backend"
Write-Host "[OK] Restart command sent!" -ForegroundColor Green

# ---------------------------------------------------------------------
# Step 5: Health Check Polling
# ---------------------------------------------------------------------
Write-Host "`n[5/5] Waiting for Backend startup & Health Check API..." -ForegroundColor Yellow
$HealthUrl = "http://${VpsHost}:8080/api/v1/health"
$StartTime = Get-Date
$IsSuccess = $false

for ($i = 1; $i -le $HealthTimeoutSeconds; $i += 3) {
    Start-Sleep -Seconds 3
    $Elapsed = [math]::Round(((Get-Date) - $StartTime).TotalSeconds)
    Write-Host "Checking health status... (${Elapsed}s)" -ForegroundColor Gray
    
    try {
        $Response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($Response.StatusCode -eq 200) {
            $IsSuccess = $true
            Write-Host "Health Check -> OK (200)" -ForegroundColor Green
            break
        }
    } catch {
        try {
            $ProxyUrl = "http://${VpsHost}/api/v1/health"
            $Response = Invoke-WebRequest -Uri $ProxyUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($Response.StatusCode -eq 200) {
                $IsSuccess = $true
                Write-Host "Health Check -> OK (200 via Nginx)" -ForegroundColor Green
                break
            }
        } catch {}
    }
}

Write-Host "`n=====================================================" -ForegroundColor Cyan
if ($IsSuccess) {
    Write-Host " [SUCCESS] DEPLOYMENT COMPLETED SUCCESSFULLY! " -ForegroundColor Black -BackgroundColor Green
    Write-Host " - Website URL: http://${VpsHost}/" -ForegroundColor Green
    Write-Host " - Health API:  http://${VpsHost}/api/v1/health" -ForegroundColor Green
    Write-Host " - Database:    gioitruyen (Flyway Migrated + Seeded Data)" -ForegroundColor Green
} else {
    Write-Host " [FAILED] BACKEND DID NOT RESPOND IN TIME OR HAS ERRORS " -ForegroundColor White -BackgroundColor Red
    Write-Host "`nFetching last 30 log lines from VPS..." -ForegroundColor Yellow
    ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "journalctl -u gioitruyen-backend -n 30 --no-pager"
}
Write-Host "=====================================================`n" -ForegroundColor Cyan
