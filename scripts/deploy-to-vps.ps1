# PowerShell deployment script for VPS 187.127.214.54
param (
    [string]$VpsHost = "187.127.214.54",
    [string]$VpsUser = "root",
    [string]$VpsPass = "Gioitruyen2026@"
)

$ErrorActionPreference = "Stop"

Write-Host "=== GioiTruyen Platform VPS Deployer ===" -ForegroundColor Green
Write-Host "Target VPS: $VpsUser@$VpsHost" -ForegroundColor Yellow

$JarPath = Join-Path $PSScriptRoot "..\backend\build\libs\story-platform-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path $JarPath)) {
    $JarCandidate = Get-ChildItem -Path (Join-Path $PSScriptRoot "..\backend\build\libs") -Filter "*.jar" | Select-Object -First 1
    if ($JarCandidate) {
        $JarPath = $JarCandidate.FullName
    } else {
        Write-Error "Backend JAR file not found. Please build backend first."
    }
}
Write-Host "Found Backend JAR: $JarPath" -ForegroundColor Cyan

# Script remote bash để chạy trên VPS
$RemoteSetupScript = @"
#!/usr/bin/env bash
set -e

echo "==> Updating APT packages..."
apt-get update -qq

echo "==> Installing OpenJDK 21 JRE, Nginx, MySQL Server..."
apt-get install -y -qq openjdk-21-jre-headless nginx mysql-server curl unzip > /dev/null

echo "==> Configuring MySQL Database and User..."
systemctl start mysql
systemctl enable mysql

mysql -e "CREATE DATABASE IF NOT EXISTS gioitruyen CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -e "CREATE USER IF NOT EXISTS 'gioitruyen'@'localhost' IDENTIFIED BY 'Gioitruyen_DB_2026!';"
mysql -e "GRANT ALL PRIVILEGES ON gioitruyen.* TO 'gioitruyen'@'localhost';"
mysql -e "FLUSH PRIVILEGES;"

echo "==> Setting up application directories..."
mkdir -p /opt/gioitruyen-backend
mkdir -p /var/www/gioitruyen-web

echo "==> Creating Systemd service for Backend..."
cat << 'EOF' > /etc/systemd/system/gioitruyen-backend.service
[Unit]
Description=GioiTruyen Spring Boot Backend Service
After=network.target mysql.service

[Service]
User=root
WorkingDirectory=/opt/gioitruyen-backend
ExecStart=/usr/bin/java -Xmx1024m -jar /opt/gioitruyen-backend/application.jar
Restart=always
RestartSec=10
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=SERVER_PORT=8080
Environment=MYSQL_URL=jdbc:mysql://localhost:3306/gioitruyen?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=UTC
Environment=MYSQL_USER=gioitruyen
Environment=MYSQL_PASSWORD=Gioitruyen_DB_2026!
Environment=MYSQL_MIGRATIONS_ENABLED=true
Environment=APP_SEED_ENABLED=true
Environment=JWT_ISSUER=story-platform-prod
Environment=JWT_AUDIENCE=story-platform-prod
Environment=JWT_SIGNING_KEY=wRhvNrlw2ELLICRQC5dubPVqCsJLhYilT0Fx0LKajU4=
Environment=EMAIL_VERIFICATION_HMAC_KEY=xh14sotJx50SghNfZKt0KAV6cjlh289nM3/LIZpytO4=
Environment=LOGIN_RISK_HMAC_KEY=s8Y63crDPkvaxRzPWSGSQv295XsaTCyZ4Khj3Hc+jW8=
Environment=MFA_ENCRYPTION_KEY=wfoWcj4Grme+NH63Y6na6XEhdyIOaF0gI8pA9w0J4Vw=
Environment=REFERRAL_CODE_HMAC_KEY=wKE6EUmsd1iNgekagV39UCrkPLY5FIIcKAownyZv1ek=

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable gioitruyen-backend

echo "==> Configuring Nginx Reverse Proxy..."
cat << 'EOF' > /etc/nginx/sites-available/gioitruyen
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    client_max_body_size 50M;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location / {
        root /var/www/gioitruyen-web;
        index index.html index.htm;
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/gioitruyen /etc/nginx/sites-enabled/gioitruyen
nginx -t
systemctl reload nginx

echo "==> VPS environment ready!"
"@

$ScriptLocalPath = Join-Path $env:TEMP "vps-setup.sh"
[System.IO.File]::WriteAllText($ScriptLocalPath, $RemoteSetupScript.Replace("`r`n", "`n"))

Write-Host "Executing setup on VPS..." -ForegroundColor Yellow
# Running setup via SSH
# Using plink/scp or ssh if available
ssh -o StrictHostKeyChecking=no "$VpsUser@$VpsHost" "bash -s" < $ScriptLocalPath

Write-Host "Uploading Backend JAR..." -ForegroundColor Yellow
scp -o StrictHostKeyChecking=no "$JarPath" "$VpsUser@${VpsHost}:/opt/gioitruyen-backend/application.jar"

Write-Host "Restarting Backend Service on VPS..." -ForegroundColor Yellow
ssh -o StrictHostKeyChecking=no "$VpsUser@$VpsHost" "systemctl restart gioitruyen-backend && systemctl status gioitruyen-backend --no-pager"

Write-Host "=== Deployment Completed Successfully ===" -ForegroundColor Green
