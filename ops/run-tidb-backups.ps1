[CmdletBinding()]
param(
    [string]$EnvironmentFile = '.env.cloudflare-tidb',
    [ValidateRange(1, 3650)]
    [int]$RetentionDays = 30,
    [switch]$UploadOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$environmentPath = if ([IO.Path]::IsPathRooted($EnvironmentFile)) {
    $EnvironmentFile
} else {
    Join-Path $projectRoot $EnvironmentFile
}
if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "The private TiDB environment file is missing: $environmentPath"
}

$allowedVariables = @('DB_URL', 'DB_USER', 'DB_PASSWORD', 'UPLOAD_DIR',
    'BACKUP_STATUS_FILE', 'UPLOAD_BACKUP_STATUS_FILE')
$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $name = $matches[1]
        if ($name -notin $allowedVariables) {
            continue
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
        throw 'Invalid line in the private TiDB environment file.'
    }
}

$requiredVariables = if ($UploadOnly) {
    @('UPLOAD_DIR')
} else {
    @('DB_URL', 'DB_USER', 'DB_PASSWORD', 'UPLOAD_DIR')
}
foreach ($required in $requiredVariables) {
    if ([string]::IsNullOrWhiteSpace($values[$required])) {
        throw "$required must be configured in the private TiDB environment file."
    }
}
$database = $null
if (-not $UploadOnly) {
    if ($values.DB_URL -notmatch '^jdbc:mysql://(?<host>[^/:?]+):(?<port>\d+)/(?<database>[A-Za-z0-9_]+)\?(?<query>[^#]+)$') {
        throw 'DB_URL must be a TiDB JDBC URL using an explicit port and database name.'
    }
    $database = $matches
    if ($database.port -ne '4000' -or
        $database.query -notmatch '(?i)(?:^|&)sslMode=VERIFY_IDENTITY(?:&|$)') {
        throw 'Database backups require TiDB Cloud port 4000 and sslMode=VERIFY_IDENTITY.'
    }
}

$backupDirectory = Join-Path $env:LOCALAPPDATA 'MITPortal\backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$backupDirectory = (Resolve-Path -LiteralPath $backupDirectory).Path
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$identitySid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$icacls = Join-Path $env:SystemRoot 'System32\icacls.exe'
& $icacls $backupDirectory '/inheritance:r' '/grant:r' `
    "*${identitySid}:(OI)(CI)F" `
    '*S-1-5-18:(OI)(CI)F' `
    '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Could not restrict the local backup directory permissions.'
}

$caPath = $null
if (-not $UploadOnly) {
    $caCertificate = Get-ChildItem -Path @('Cert:\LocalMachine\Root', 'Cert:\CurrentUser\Root') |
        Where-Object {
            $_.Thumbprint -eq 'CABD2A79A1076A31F21D253635CB039D4329A5E8' -and
            $_.Subject -match 'ISRG Root X1' -and
            $_.Issuer -eq $_.Subject
        } |
        Select-Object -First 1
    if (-not $caCertificate) {
        throw 'The trusted ISRG Root X1 certificate is not installed in the Windows root certificate store.'
    }
    $certificateDirectory = Join-Path $env:LOCALAPPDATA 'MITPortal\certificates'
    New-Item -ItemType Directory -Path $certificateDirectory -Force | Out-Null
    $caPath = Join-Path $certificateDirectory 'isrg-root-x1.pem'
    $base64Certificate = [Convert]::ToBase64String(
        $caCertificate.RawData,
        [Base64FormattingOptions]::InsertLineBreaks
    )
    $pemCertificate = "-----BEGIN CERTIFICATE-----`r`n$base64Certificate`r`n-----END CERTIFICATE-----`r`n"
    [IO.File]::WriteAllText($caPath, $pemCertificate, (New-Object Text.UTF8Encoding($false)))
}

$databaseStatusFile = if ($values.BACKUP_STATUS_FILE) {
    $values.BACKUP_STATUS_FILE
} else {
    Join-Path $backupDirectory '.database-backup-success'
}
$uploadStatusFile = if ($values.UPLOAD_BACKUP_STATUS_FILE) {
    $values.UPLOAD_BACKUP_STATUS_FILE
} else {
    Join-Path $backupDirectory '.uploads-backup-success'
}
$uploadDirectory = if ([IO.Path]::IsPathRooted($values.UPLOAD_DIR)) {
    $values.UPLOAD_DIR
} else {
    Join-Path $projectRoot $values.UPLOAD_DIR
}
if (-not (Test-Path -LiteralPath $uploadDirectory -PathType Container)) {
    throw 'The configured upload directory does not exist; no upload backup was made.'
}

if (-not $UploadOnly) {
    $env:DB_PASSWORD = $values.DB_PASSWORD
    $env:DB_USER = $values.DB_USER
}

$backupErrors = [System.Collections.Generic.List[string]]::new()
Push-Location $projectRoot
try {
    if (-not $UploadOnly) {
        try {
            & (Join-Path $PSScriptRoot 'backup-tidb-dumpling.ps1') `
                -OutputDirectory $backupDirectory `
                -DatabaseHost $database.host `
                -DatabasePort ([int]$database.port) `
                -DatabaseName $database.database `
                -DatabaseUser $values.DB_USER `
                -CertificatePath $caPath `
                -StatusFile $databaseStatusFile `
                -RetentionDays $RetentionDays
        } catch {
            $backupErrors.Add("Database backup failed: $($_.Exception.Message)")
        }
    }

    try {
        & (Join-Path $PSScriptRoot 'backup-uploads.ps1') `
            -UploadDirectory $uploadDirectory `
            -OutputDirectory $backupDirectory `
            -StatusFile $uploadStatusFile `
            -RetentionDays $RetentionDays
    } catch {
        $backupErrors.Add("Upload backup failed: $($_.Exception.Message)")
    }

    if ($backupErrors.Count -gt 0) {
        throw ($backupErrors -join [Environment]::NewLine)
    }
} finally {
    if (-not $UploadOnly) {
        Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:DB_USER -ErrorAction SilentlyContinue
    }
    Pop-Location
}
