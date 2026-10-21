[CmdletBinding()]
param(
    [string]$EnvironmentFile = '.env.cloudflare-tidb',
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$environmentPath = if ([IO.Path]::IsPathRooted($EnvironmentFile)) {
    $EnvironmentFile
} else {
    Join-Path $projectRoot $EnvironmentFile
}
$templatePath = Join-Path $projectRoot '.env.cloudflare-tidb.example'

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    Copy-Item -LiteralPath $templatePath -Destination $environmentPath
    throw "Created the private environment template at $environmentPath. Fill in the TiDB connection, Cloudflare hostname, and fresh secrets, then rerun this command."
}

$allowedVariables = @(
    'SPRING_PROFILES_ACTIVE',
    'DB_URL',
    'DB_USER',
    'DB_PASSWORD',
    'JWT_SECRET',
    'PAYMENT_WEBHOOK_SECRET',
    'FRONTEND_URL',
    'CORS_ALLOWED_ORIGINS',
    'COOKIE_SECURE',
    'COOKIE_SAME_SITE',
    'SERVER_PORT',
    'UPLOAD_DIR',
    'SAMPLE_DATA_ENABLED',
    'FLYWAY_BASELINE_ON_MIGRATE',
    'MAIL_ENABLED',
    'MAIL_PROVIDER',
    'MAIL_HOST',
    'MAIL_PORT',
    'MAIL_USERNAME',
    'MAIL_PASSWORD',
    'MAIL_STARTTLS_ENABLE',
    'MAIL_STARTTLS_REQUIRED',
    'MAIL_SMTP_SSL_ENABLE',
    'MAIL_API_KEY',
    'MAIL_FROM_EMAIL',
    'MAIL_FROM_NAME',
    'MFA_ENABLED',
    'MFA_ENCRYPTION_KEY',
    'INITIAL_ADMIN_BOOTSTRAP_ENABLED',
    'INITIAL_ADMIN_USERNAME',
    'INITIAL_ADMIN_EMAIL',
    'INITIAL_ADMIN_PASSWORD',
    'BACKUP_STATUS_FILE',
    'UPLOAD_BACKUP_STATUS_FILE',
    'BACKUP_MAX_AGE_HOURS',
    'AUTH_TRUSTED_PROXY_IPS',
    'AUTH_TRUST_X_FORWARDED_FOR'
)

$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $name = $matches[1]
        if ($name -notin $allowedVariables) {
            throw "Unsupported setting '$name' in the Cloudflare/TiDB environment file."
        }

        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        } else {
            $value = ($value -replace '\s+#.*$', '').TrimEnd()
        }
        $values[$name] = $value
    } elseif (-not [string]::IsNullOrWhiteSpace($line) -and $line -notmatch '^\s*#') {
        throw 'Invalid line in the Cloudflare/TiDB environment file. Use NAME=value syntax.'
    }
}

foreach ($name in @(
    'DB_URL', 'DB_USER', 'DB_PASSWORD', 'JWT_SECRET', 'PAYMENT_WEBHOOK_SECRET',
    'FRONTEND_URL', 'CORS_ALLOWED_ORIGINS', 'UPLOAD_DIR'
)) {
    if ([string]::IsNullOrWhiteSpace($values[$name]) -or
        $values[$name] -match '(?i)replace-with|YOUR_|example\.edu') {
        throw "$name must be replaced with a real value in $environmentPath."
    }
}

if ($values.SPRING_PROFILES_ACTIVE -ne 'prod') {
    throw 'SPRING_PROFILES_ACTIVE must be prod for the public Cloudflare tunnel setup.'
}
if ($values.COOKIE_SECURE -ne 'true' -or $values.COOKIE_SAME_SITE -ne 'Strict') {
    throw 'Set COOKIE_SECURE=true and COOKIE_SAME_SITE=Strict for the HTTPS public portal.'
}
if ($values.SAMPLE_DATA_ENABLED -ne 'false' -or $values.FLYWAY_BASELINE_ON_MIGRATE -ne 'false') {
    throw 'Keep sample data disabled and automatic Flyway baselining disabled for the TiDB portal.'
}
if ($values.DB_USER -match '(?i)(^|[.])root$' -or $values.DB_USER -notmatch '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$') {
    throw 'DB_USER must be a TiDB cluster-prefixed, non-root application username.'
}
if ($values.JWT_SECRET.Length -lt 32) {
    throw 'JWT_SECRET must contain at least 32 characters.'
}
if ($values.PAYMENT_WEBHOOK_SECRET.Length -lt 32) {
    throw 'PAYMENT_WEBHOOK_SECRET must contain at least 32 characters.'
}
if ($values.MFA_ENABLED -eq 'true') {
    try {
        $mfaKeyBytes = [Convert]::FromBase64String($values.MFA_ENCRYPTION_KEY)
    } catch {
        throw 'MFA_ENCRYPTION_KEY must be Base64-encoded when MFA_ENABLED=true.'
    }
    if ($mfaKeyBytes.Length -ne 32) {
        throw 'MFA_ENCRYPTION_KEY must decode to exactly 32 bytes when MFA_ENABLED=true.'
    }
}

if ($values.DB_URL -notmatch '^jdbc:mysql://(?<host>[^/:?]+):4000/(?<database>[A-Za-z0-9_]+)\?(?<query>[^#]+)$') {
    throw 'DB_URL must be a TiDB Cloud JDBC URL using port 4000 and a database name.'
}
$tidbUrl = $matches
if ($tidbUrl.host -match '(?i)YOUR_|localhost|127\.0\.0\.1' -or
    $tidbUrl.query -notmatch '(?i)(?:^|&)sslMode=VERIFY_IDENTITY(?:&|$)') {
    throw 'DB_URL must use the real TiDB hostname and sslMode=VERIFY_IDENTITY.'
}
$databaseHost = $tidbUrl.host
$databaseName = $tidbUrl.database
$jdbcOptions = $tidbUrl.query
if ($databaseName -in @('sys', 'mysql', 'information_schema', 'performance_schema')) {
    throw 'DB_URL must target a dedicated application database, not a system database.'
}

$frontendUri = $null
if (-not [Uri]::TryCreate($values.FRONTEND_URL, [UriKind]::Absolute, [ref]$frontendUri) -or
    $frontendUri.Scheme -ne 'https' -or
    [string]::IsNullOrWhiteSpace($frontendUri.Host) -or
    $frontendUri.AbsolutePath -ne '/' -or
    -not [string]::IsNullOrEmpty($frontendUri.Query) -or
    -not [string]::IsNullOrEmpty($frontendUri.Fragment) -or
    -not [string]::IsNullOrEmpty($frontendUri.UserInfo)) {
    throw 'FRONTEND_URL must be the exact public HTTPS origin, without a path, query, or fragment.'
}
if ($values.CORS_ALLOWED_ORIGINS -ne $values.FRONTEND_URL) {
    throw 'CORS_ALLOWED_ORIGINS must exactly match FRONTEND_URL; do not add wildcards or trailing slashes.'
}

$mailEnabled = $values.MAIL_ENABLED -eq 'true'
if ($mailEnabled) {
    if ($values.MAIL_PROVIDER -eq 'resend') {
        foreach ($name in @('MAIL_API_KEY', 'MAIL_FROM_EMAIL')) {
            if ([string]::IsNullOrWhiteSpace($values[$name]) -or $values[$name] -match '(?i)replace-with|YOUR_') {
                throw "$name is required when Resend email is enabled."
            }
        }
    } elseif ($values.MAIL_PROVIDER -eq 'smtp') {
        foreach ($name in @('MAIL_HOST', 'MAIL_USERNAME', 'MAIL_PASSWORD')) {
            if ([string]::IsNullOrWhiteSpace($values[$name]) -or $values[$name] -match '(?i)replace-with|YOUR_') {
                throw "$name is required when SMTP email is enabled."
            }
        }
    } else {
        throw 'MAIL_PROVIDER must be smtp or resend when email is enabled.'
    }
}

$bootstrapSetting = [string]$values.INITIAL_ADMIN_BOOTSTRAP_ENABLED
if (-not [string]::IsNullOrWhiteSpace($bootstrapSetting) -and
    $bootstrapSetting -notmatch '(?i)^(true|false)$') {
    throw 'INITIAL_ADMIN_BOOTSTRAP_ENABLED must be true or false.'
}
$bootstrapEnabled = $bootstrapSetting -eq 'true'
if ($bootstrapEnabled) {
    if ($values.SAMPLE_DATA_ENABLED -ne 'false') {
        throw 'Keep SAMPLE_DATA_ENABLED=false when bootstrapping the initial administrator.'
    }
    if ($values.INITIAL_ADMIN_USERNAME -notmatch '^[A-Za-z0-9._@+-]{3,64}$') {
        throw 'INITIAL_ADMIN_USERNAME must contain 3 to 64 letters, digits, or . _ @ + - characters.'
    }
    if ($values.INITIAL_ADMIN_EMAIL -notmatch '(?i)^[A-Z0-9.!#$%&''*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$') {
        throw 'INITIAL_ADMIN_EMAIL must be a valid email address.'
    }
    $bootstrapPasswordBytes = [Text.Encoding]::UTF8.GetByteCount([string]$values.INITIAL_ADMIN_PASSWORD)
    if ($bootstrapPasswordBytes -lt 12 -or $bootstrapPasswordBytes -gt 72 -or
        $values.INITIAL_ADMIN_USERNAME.Trim() -eq $values.INITIAL_ADMIN_PASSWORD) {
        throw 'INITIAL_ADMIN_PASSWORD must be 12 to 72 UTF-8 bytes and differ from the username.'
    }
} elseif (-not [string]::IsNullOrWhiteSpace($values.INITIAL_ADMIN_USERNAME) -or
    -not [string]::IsNullOrWhiteSpace($values.INITIAL_ADMIN_EMAIL) -or
    -not [string]::IsNullOrWhiteSpace($values.INITIAL_ADMIN_PASSWORD)) {
    throw 'Clear initial administrator bootstrap credentials after the one-time account has been created.'
}

$uploadDirectory = if ([IO.Path]::IsPathRooted($values.UPLOAD_DIR)) {
    $values.UPLOAD_DIR
} else {
    Join-Path $projectRoot $values.UPLOAD_DIR
}
New-Item -ItemType Directory -Force -Path $uploadDirectory | Out-Null
if (-not (Test-Path -LiteralPath $uploadDirectory -PathType Container)) {
    throw "UPLOAD_DIR is not a writable directory: $uploadDirectory"
}

foreach ($name in $allowedVariables) {
    if ($values.ContainsKey($name)) {
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
}
$env:DB_URL = "jdbc:mysql://${databaseHost}:4000/${databaseName}?${jdbcOptions}"
$env:UPLOAD_DIR = [IO.Path]::GetFullPath($uploadDirectory)
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = [string]$Port
$backupDirectory = Join-Path $env:LOCALAPPDATA 'MITPortal\backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
if ([string]::IsNullOrWhiteSpace($values.BACKUP_STATUS_FILE)) {
    $values.BACKUP_STATUS_FILE = Join-Path $backupDirectory '.database-backup-success'
}
if ([string]::IsNullOrWhiteSpace($values.UPLOAD_BACKUP_STATUS_FILE)) {
    $values.UPLOAD_BACKUP_STATUS_FILE = Join-Path $backupDirectory '.uploads-backup-success'
}
if ([string]::IsNullOrWhiteSpace($values.BACKUP_MAX_AGE_HOURS)) {
    $values.BACKUP_MAX_AGE_HOURS = '36'
}
$env:BACKUP_STATUS_FILE = [IO.Path]::GetFullPath($values.BACKUP_STATUS_FILE)
$env:UPLOAD_BACKUP_STATUS_FILE = [IO.Path]::GetFullPath($values.UPLOAD_BACKUP_STATUS_FILE)
$env:BACKUP_MAX_AGE_HOURS = $values.BACKUP_MAX_AGE_HOURS
$env:AUTH_TRUSTED_PROXY_IPS = '127.0.0.1,::1,::ffff:127.0.0.1'
$env:AUTH_TRUST_X_FORWARDED_FOR = 'true'

Write-Output "Configuration checks passed for the HTTPS origin $($values.FRONTEND_URL)."
Write-Output "TiDB target: $databaseHost`:4000/$databaseName (TLS identity verification enabled)."
Write-Output "Java will bind only to 127.0.0.1:$Port. No database password or signing secret was displayed."
Write-Output 'These checks validate configuration syntax only; they do not verify TiDB network access or migration compatibility.'
if ($ValidateOnly) {
    return
}

Write-Output "Starting Spring Boot with the production profile. The named Cloudflare Tunnel must already route this hostname to http://127.0.0.1:$Port."
Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'mvnw.cmd') -B spring-boot:run `
        "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=$Port"
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot exited with code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
