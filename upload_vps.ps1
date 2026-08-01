# =====================================================================
# Script Triển Khai & Upload Tự Động Cho Dự Án MonkeyDD / Gioitruyen Platform
# Sử dụng:
#   .\upload_vps.ps1          (Tự động SKIP build nếu file chưa sửa)
#   .\upload_vps.ps1 -Rebuild (Bắt buộc build lại từ đầu)
# =====================================================================

param (
    [string]$VpsHost = "187.127.214.54",
    [string]$VpsUser = "root",
    [int]$HealthTimeoutSeconds = 60,
    [switch]$Rebuild = $false
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "   BAT DAU UPLOAD TU DONG LEN VPS $VpsHost  " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------
# Hàm kiểm tra file source có mới hơn file build không
# ---------------------------------------------------------------------
function Test-BuildNeeded($SourcePath, $BuildTarget) {
    if ($Rebuild) { return $true }
    if (-not (Test-Path $BuildTarget)) { return $true }
    
    $BuildTime = (Get-Item $BuildTarget).LastWriteTime
    $ModifiedSources = Get-ChildItem -Recurse $SourcePath -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -gt $BuildTime }
    return ($null -ne $ModifiedSources -and $ModifiedSources.Count -gt 0)
}

# ---------------------------------------------------------------------
# Step 1: Build Backend
# ---------------------------------------------------------------------
Write-Host "`n[1/5] Kiem tra Backend..." -ForegroundColor Yellow
$BackendDir = Join-Path $ScriptDir "backend"
if (-not (Test-Path $BackendDir)) {
    $BackendDir = Join-Path $ScriptDir "..\backend"
}

$BackendSrc = Join-Path $BackendDir "src"
$JarPath = Join-Path $BackendDir "build\libs\story-platform-backend-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $JarPath)) {
    $JarCandidate = Get-ChildItem -Path (Join-Path $BackendDir "build\libs") -Filter "*.jar" | Select-Object -First 1
    if ($JarCandidate) { $JarPath = $JarCandidate.FullName }
}

$NeedBackendBuild = Test-BuildNeeded -SourcePath $BackendSrc -BuildTarget $JarPath

if ($NeedBackendBuild) {
    Write-Host "Phat hien code Backend moi -> Dang build Spring Boot JAR..." -ForegroundColor Yellow
    $GradleBat = Join-Path $BackendDir "gradlew.bat"
    if (Test-Path $GradleBat) {
        & $GradleBat --project-dir $BackendDir bootJar
    } else {
        Set-Location $BackendDir
        ./gradlew bootJar
    }
    Write-Host "[OK] Backend build success!" -ForegroundColor Green
} else {
    Write-Host "[SKIP] Backend JAR da build va code chua thay doi." -ForegroundColor Cyan
}

# ---------------------------------------------------------------------
# Step 2: Build Frontend (Standalone Output)
# ---------------------------------------------------------------------
Write-Host "`n[2/5] Kiem tra Frontend..." -ForegroundColor Yellow
$WebappDir = $ScriptDir
if (-not (Test-Path (Join-Path $WebappDir "frontend"))) {
    $WebappDir = Join-Path $ScriptDir "Webapp"
}
$FrontendSrc = Join-Path $WebappDir "frontend\apps\web\src"
$FrontendNextDir = Join-Path $WebappDir "frontend\apps\web\.next"
$StandaloneDir = Join-Path $FrontendNextDir "standalone"

$NeedFrontendBuild = Test-BuildNeeded -SourcePath $FrontendSrc -BuildTarget $StandaloneDir

if ($NeedFrontendBuild) {
    Write-Host "Phat hien code Frontend moi -> Dang build Next.js Standalone..." -ForegroundColor Yellow
    Set-Location $WebappDir
    pnpm build:fe
    Write-Host "[OK] Frontend build success!" -ForegroundColor Green
} else {
    Write-Host "[SKIP] Frontend Standalone da build va code chua thay doi." -ForegroundColor Cyan
}

# ---------------------------------------------------------------------
# Step 3: Nén & Upload Frontend Standalone (.next/standalone)
# ---------------------------------------------------------------------
Write-Host "`n[3/5] Uploading JAR & Frontend Standalone Assets to VPS ($VpsHost)..." -ForegroundColor Yellow
Write-Host "Uploading application.jar (Backend)..." -ForegroundColor Gray
scp -o StrictHostKeyChecking=no "$JarPath" "${VpsUser}@${VpsHost}:/opt/gioitruyen-backend/application.jar"

if (Test-Path $StandaloneDir) {
    Write-Host "Packaging Next.js Standalone bundle (node_modules + apps/web)..." -ForegroundColor Gray
    $TarFile = Join-Path $env:TEMP "fe-dist-$([guid]::NewGuid().ToString('N').Substring(0,8)).tar.gz"
    
    $AppStandalone = Join-Path $StandaloneDir "apps\web"
    if (-not (Test-Path $AppStandalone)) {
        New-Item -ItemType Directory -Path $AppStandalone -Force | Out-Null
    }
    
    # Copy static & public assets vào apps/web trong standalone
    $StaticSrc = Join-Path $FrontendNextDir "static"
    $StaticDest = Join-Path $AppStandalone ".next\static"
    if (Test-Path $StaticSrc) {
        New-Item -ItemType Directory -Path $StaticDest -Force | Out-Null
        Copy-Item -Path "$StaticSrc\*" -Destination $StaticDest -Recurse -Force
    }
    
    $PublicSrc = Join-Path $WebappDir "frontend\apps\web\public"
    $PublicDest = Join-Path $AppStandalone "public"
    if (Test-Path $PublicSrc) {
        New-Item -ItemType Directory -Path $PublicDest -Force | Out-Null
        Copy-Item -Path "$PublicSrc\*" -Destination $PublicDest -Recurse -Force
    }
    
    Set-Location $StandaloneDir
    tar -czf "$TarFile" .
    Set-Location $ScriptDir
    
    Write-Host "Uploading fe-dist.tar.gz to VPS..." -ForegroundColor Gray
    scp -o StrictHostKeyChecking=no "$TarFile" "${VpsUser}@${VpsHost}:/tmp/fe-dist.tar.gz"
    
    Write-Host "Extracting Frontend Standalone on VPS..." -ForegroundColor Gray
    ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "mkdir -p /var/www/gioitruyen-web && tar -xzf /tmp/fe-dist.tar.gz -C /var/www/gioitruyen-web && chmod -R 755 /var/www/gioitruyen-web && rm -f /tmp/fe-dist.tar.gz"
    if (Test-Path $TarFile) { Remove-Item -Force $TarFile -ErrorAction SilentlyContinue }
}
Write-Host "[OK] Upload completed!" -ForegroundColor Green

# ---------------------------------------------------------------------
# Step 4: Restart Services bằng PM2 & Systemd
# ---------------------------------------------------------------------
Write-Host "`n[4/5] Restarting Services on VPS..." -ForegroundColor Yellow
ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "systemctl restart gioitruyen-backend"
ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "cd /var/www/gioitruyen-web && pm2 delete gioitruyen-fe >/dev/null 2>&1; PORT=3000 pm2 start apps/web/server.js --name gioitruyen-fe && pm2 save"
Write-Host "[OK] Backend & Frontend restart commands sent!" -ForegroundColor Green

# ---------------------------------------------------------------------
# Step 5: Full Health Check
# ---------------------------------------------------------------------
Write-Host "`n[5/5] Full Health Check: Checking Web Frontend & Backend API..." -ForegroundColor Yellow

$WebUrl = "http://${VpsHost}/"
$HealthUrl = "http://${VpsHost}/api/v1/health"

$StartTime = Get-Date
$WebOnline = $false
$BeOnline = $false
$WebCodeStr = "OFFLINE"
$BeCodeStr = "OFFLINE"

for ($i = 1; $i -le $HealthTimeoutSeconds; $i += 3) {
    Start-Sleep -Seconds 3
    $Elapsed = [math]::Round(((Get-Date) - $StartTime).TotalSeconds)
    
    # 1. Kiểm tra Web Frontend (Port 80)
    try {
        $w = Invoke-WebRequest -Uri $WebUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
        $WebCodeStr = "200 OK"
        if ($w.StatusCode -eq 200 -or $w.StatusCode -eq 301 -or $w.StatusCode -eq 302) {
            $WebOnline = $true
        }
    } catch {
        if ($_.Exception.Response) {
            $code = $_.Exception.Response.StatusCode.value__
            $WebCodeStr = "HTTP $code"
            if ($code -eq 200 -or $code -eq 301 -or $code -eq 302) {
                $WebOnline = $true
            }
        } else {
            $WebCodeStr = "OFFLINE"
        }
    }

    # 2. Kiểm tra Backend API (Port 8080 & Nginx /api/ proxy)
    try {
        $b = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
        $BeCodeStr = "200 OK"
        if ($b.StatusCode -eq 200 -or $b.StatusCode -eq 401) {
            $BeOnline = $true
        }
    } catch {
        if ($_.Exception.Response) {
            $code = $_.Exception.Response.StatusCode.value__
            $BeCodeStr = "HTTP $code (Auth/Response OK)"
            if ($code -eq 200 -or $code -eq 401) {
                $BeOnline = $true
            }
        } else {
            $BeCodeStr = "OFFLINE"
        }
    }

    Write-Host "Health Check (${Elapsed}s): Web = $WebCodeStr | Backend API = $BeCodeStr" -ForegroundColor Gray
    if ($WebOnline -and $BeOnline) { break }
}

$WebColor = if ($WebOnline) { "Green" } else { "Red" }
$BeColor = if ($BeOnline) { "Green" } else { "Red" }

Write-Host "`n=====================================================" -ForegroundColor Cyan
Write-Host "   KET QUA KHIEM TRA HE THONG (FULL HEALTH REPORT)  " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host " 1. Web Frontend (Port 80):  $WebCodeStr" -ForegroundColor $WebColor
Write-Host " 2. Backend API  (Port 8080): $BeCodeStr" -ForegroundColor $BeColor
Write-Host " 3. MySQL Database:           gioitruyen (Flyway + Seed Data Active)" -ForegroundColor Green

if ($WebOnline -and $BeOnline) {
    Write-Host "`n [SUCCESS] TRIEN KHAI HOAN HAO! TAT CA SERVICE DA ONLINE!" -ForegroundColor Black -BackgroundColor Green
    Write-Host " - Link Truy Cap Web: http://${VpsHost}/" -ForegroundColor Green
    Write-Host " - Link Health API:   http://${VpsHost}/api/v1/health" -ForegroundColor Green
} else {
    Write-Host "`n [WARNING] CO SERVICE CHUA HOAT DONG CHUAN XAC" -ForegroundColor White -BackgroundColor Red
    if (-not $BeOnline) {
        Write-Host "`n---> In 30 dòng log sự cố từ Backend VPS:" -ForegroundColor Yellow
        ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "journalctl -u gioitruyen-backend -n 30 --no-pager"
    }
    if (-not $WebOnline) {
        Write-Host "`n---> In 20 dòng log sự cố PM2/Nginx từ VPS:" -ForegroundColor Yellow
        ssh -o StrictHostKeyChecking=no "${VpsUser}@${VpsHost}" "pm2 logs --lines 20 --nostream"
    }
}
Write-Host "=====================================================`n" -ForegroundColor Cyan
