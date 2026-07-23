[CmdletBinding()]
param(
    [string]$Path
)

if ([string]::IsNullOrWhiteSpace($Path)) {
    $scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
    $Path = Join-Path $scriptDirectory "..\.env"
}

$resolvedPath = Resolve-Path -LiteralPath $Path -ErrorAction Stop
$values = @{}

foreach ($rawLine in Get-Content -LiteralPath $resolvedPath -Encoding utf8) {
    $line = $rawLine.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) {
        continue
    }

    $separator = $line.IndexOf("=")
    if ($separator -le 0) {
        throw "Invalid environment line. Expected KEY=VALUE."
    }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim().Trim('"').Trim("'")

    if ($name -notmatch "^[A-Z][A-Z0-9_]*$") {
        throw "Invalid environment variable name '$name'."
    }
    if ($values.ContainsKey($name)) {
        throw "Duplicate environment variable '$name'."
    }

    $values[$name] = $value
}

$required = @(
    "APP_NAME",
    "APP_ENV",
    "API_PORT",
    "API_BASE_URL",
    "WEB_BASE_URL",
    "MONGODB_URI",
    "MONGODB_DATABASE",
    "REDIS_URL",
    "JWT_ISSUER",
    "JWT_AUDIENCE",
    "JWT_SIGNING_KEY",
    "DATA_ENCRYPTION_KEY",
    "TOPUP_DISCOUNT_PERCENT",
    "TOPUP_REQUEST_TTL_MINUTES",
    "WITHDRAWAL_MIN_XU",
    "WITHDRAWAL_FEE_THRESHOLD_XU",
    "WITHDRAWAL_FEE_XU"
)

$missing = @($required | Where-Object {
    -not $values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($values[$_])
})
if ($missing.Count -gt 0) {
    throw "Missing required environment variables: $($missing -join ', ')"
}

if ($values["JWT_SIGNING_KEY"].Length -lt 32) {
    throw "JWT_SIGNING_KEY must contain at least 32 characters."
}
if ($values["DATA_ENCRYPTION_KEY"].Length -lt 32) {
    throw "DATA_ENCRYPTION_KEY must contain at least 32 characters."
}

$integerRules = @{
    API_PORT = @{ Min = 1; Max = 65535 }
    TOPUP_REQUEST_TTL_MINUTES = @{ Min = 1; Max = 1440 }
    WITHDRAWAL_MIN_XU = @{ Min = 1; Max = [long]::MaxValue }
    WITHDRAWAL_FEE_THRESHOLD_XU = @{ Min = 1; Max = [long]::MaxValue }
    WITHDRAWAL_FEE_XU = @{ Min = 0; Max = [long]::MaxValue }
}

foreach ($entry in $integerRules.GetEnumerator()) {
    $parsed = 0L
    if (-not [long]::TryParse($values[$entry.Key], [ref]$parsed)) {
        throw "$($entry.Key) must be an integer."
    }
    if ($parsed -lt $entry.Value.Min -or $parsed -gt $entry.Value.Max) {
        throw "$($entry.Key) is outside the allowed range."
    }
}

$discount = 0.0
if (-not [double]::TryParse(
    $values["TOPUP_DISCOUNT_PERCENT"],
    [Globalization.NumberStyles]::Number,
    [Globalization.CultureInfo]::InvariantCulture,
    [ref]$discount
)) {
    throw "TOPUP_DISCOUNT_PERCENT must be an invariant numeric value."
}
if ($discount -lt 0 -or $discount -ge 100) {
    throw "TOPUP_DISCOUNT_PERCENT must be in the range [0, 100)."
}

$minWithdrawal = [long]$values["WITHDRAWAL_MIN_XU"]
$feeThreshold = [long]$values["WITHDRAWAL_FEE_THRESHOLD_XU"]
$fee = [long]$values["WITHDRAWAL_FEE_XU"]

if ($feeThreshold -lt $minWithdrawal) {
    throw "WITHDRAWAL_FEE_THRESHOLD_XU must be at least WITHDRAWAL_MIN_XU."
}
if ($fee -ge $minWithdrawal) {
    throw "WITHDRAWAL_FEE_XU must be lower than WITHDRAWAL_MIN_XU."
}

if ($values["APP_ENV"] -ne "local") {
    $externalSecrets = @(
        "CLOUDINARY_CLOUD_NAME",
        "CLOUDINARY_API_KEY",
        "CLOUDINARY_API_SECRET",
        "CLOUDINARY_WEBHOOK_SECRET",
        "CLOUDFLARE_ACCOUNT_ID",
        "CLOUDFLARE_ZONE_ID",
        "CLOUDFLARE_API_TOKEN"
    )

    $placeholders = @($externalSecrets | Where-Object {
        -not $values.ContainsKey($_) -or
        [string]::IsNullOrWhiteSpace($values[$_]) -or
        $values[$_] -eq "change-me"
    })
    if ($placeholders.Count -gt 0) {
        throw "Non-local environments require managed values for: $($placeholders -join ', ')"
    }
}

Write-Host "Environment contract is valid for APP_ENV=$($values['APP_ENV'])."
