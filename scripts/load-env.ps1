[CmdletBinding()]
param(
    [string]$Path,
    [switch]$Override
)

if ([string]::IsNullOrWhiteSpace($Path)) {
    $scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
    $Path = Join-Path $scriptDirectory "..\.env"
}

$resolvedPath = Resolve-Path -LiteralPath $Path -ErrorAction Stop

foreach ($rawLine in Get-Content -LiteralPath $resolvedPath -Encoding utf8) {
    $line = $rawLine.Trim()

    if ($line.Length -eq 0 -or $line.StartsWith("#")) {
        continue
    }

    $separator = $line.IndexOf("=")
    if ($separator -le 0) {
        throw "Invalid environment line in $resolvedPath. Expected KEY=VALUE."
    }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()

    if ($name -notmatch "^[A-Z][A-Z0-9_]*$") {
        throw "Invalid environment variable name '$name'."
    }

    if (
        $value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))
    ) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    $currentValue = [Environment]::GetEnvironmentVariable($name, "Process")
    if ($Override -or [string]::IsNullOrEmpty($currentValue)) {
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

Write-Host "Loaded local environment from $resolvedPath into the current process."
