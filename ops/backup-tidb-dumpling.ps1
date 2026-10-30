[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatabaseHost,
    [ValidateRange(1, 65535)][int]$DatabasePort = 4000,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName,
    [Parameter(Mandatory = $true)][string]$DatabaseUser,
    [Parameter(Mandatory = $true)][string]$CertificatePath,
    [string]$OutputDirectory = (Join-Path $env:LOCALAPPDATA 'MITPortal\backups'),
    [string]$StatusFile,
    [ValidateRange(1, 3650)][int]$RetentionDays = 30,
    [ValidateRange(30, 600)][int]$DockerStartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$expectedPackageSha256 = '9107080DF0BC3D21BD6B96CA4B990B54D46A22F9152E4B9DC4274596B82A7C06'
$dockerImage = 'ubuntu@sha256:5ec03bb3441e8b0bf3b4f9cd4629a1ae763010dc3035bb8da3ae6cf026486401'
$version = '8.5.8'
$packagePath = Join-Path $env:LOCALAPPDATA "MITPortal\tools\dumpling-v$version-linux-amd64.tar.gz"

if (-not $env:DB_PASSWORD) {
    throw 'DB_PASSWORD must be configured for the TiDB backup.'
}
if ($env:DB_PASSWORD.Contains("`r") -or $env:DB_PASSWORD.Contains("`n")) {
    throw 'DB_PASSWORD must not contain line breaks.'
}
if (-not (Test-Path -LiteralPath $packagePath -PathType Leaf)) {
    throw 'The verified Dumpling package is missing; run ops\install-tidb-dumpling.ps1 first.'
}
if ((Get-FileHash -LiteralPath $packagePath -Algorithm SHA256).Hash -ne $expectedPackageSha256) {
    throw 'The installed Dumpling package failed its pinned SHA-256 check.'
}
if (-not (Test-Path -LiteralPath $CertificatePath -PathType Leaf)) {
    throw 'The TiDB TLS CA certificate is missing.'
}

$dockerCommand = Get-Command 'docker.exe', 'docker' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $dockerCommand) {
    throw 'Docker Desktop is required to run the official Linux Dumpling package.'
}
$dockerPath = $dockerCommand.Source

function Get-DockerServerVersion {
    try {
        $versionOutput = @(& $dockerPath version --format '{{.Server.Version}}' 2>$null)
        if ($LASTEXITCODE -eq 0) {
            return ($versionOutput -join '').Trim()
        }
    } catch {
        return $null
    }
    return $null
}

$dockerVersion = Get-DockerServerVersion
if ([string]::IsNullOrWhiteSpace($dockerVersion)) {
    $desktopPath = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $desktopPath -PathType Leaf)) {
        throw 'Docker Desktop Linux engine is unavailable and Docker Desktop could not be started.'
    }
    if (-not (Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue)) {
        Start-Process -FilePath $desktopPath
    }
    $deadline = [DateTime]::UtcNow.AddSeconds($DockerStartupTimeoutSeconds)
    do {
        Start-Sleep -Seconds 5
        $dockerVersion = Get-DockerServerVersion
    } while ([string]::IsNullOrWhiteSpace($dockerVersion) -and
        [DateTime]::UtcNow -lt $deadline)
    if ([string]::IsNullOrWhiteSpace($dockerVersion)) {
        throw "Docker Desktop Linux engine did not become ready within $DockerStartupTimeoutSeconds seconds."
    }
}

if ([string]::IsNullOrWhiteSpace($StatusFile)) {
    $StatusFile = Join-Path $OutputDirectory '.database-backup-success'
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$identitySid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$icacls = Join-Path $env:SystemRoot 'System32\icacls.exe'
& $icacls $OutputDirectory '/inheritance:r' '/grant:r' `
    "*${identitySid}:(OI)(CI)F" `
    '*S-1-5-18:(OI)(CI)F' `
    '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Could not restrict the database backup directory permissions.'
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$guid = [guid]::NewGuid().ToString('N')
$stagingDirectory = Join-Path $OutputDirectory ".database-export-$guid"
$exportDirectory = Join-Path $stagingDirectory 'export'
$publishedArchive = Join-Path $OutputDirectory "database-$timestamp.zip"
$temporaryArchive = "$publishedArchive.partial"
$checksumFile = "$publishedArchive.sha256"
$checksumTemporary = "$checksumFile.tmp"
$statusDirectory = Split-Path -Parent $StatusFile
if ($statusDirectory) {
    New-Item -ItemType Directory -Path $statusDirectory -Force | Out-Null
}
$statusTemporary = "$StatusFile.tmp"
$secretDirectory = Join-Path $env:LOCALAPPDATA 'MITPortal\secrets'
$credentialPath = $null
$containerScriptPath = $null

if (Test-Path -LiteralPath $publishedArchive) {
    throw "A database backup already exists for timestamp $timestamp; refusing to overwrite it."
}

try {
    New-Item -ItemType Directory -Path $stagingDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    & $icacls $secretDirectory '/inheritance:r' '/grant:r' `
        "*${identitySid}:(OI)(CI)F" `
        '*S-1-5-18:(OI)(CI)F' `
        '*S-1-5-32-544:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not restrict the temporary TiDB credential directory permissions.'
    }

    $credentialPath = Join-Path $secretDirectory "dumpling-$guid.env"
    $encodedPassword = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:DB_PASSWORD))

    $containerScript = @'
set -eu
mkdir -p /tmp/dumpling-bin /backup/export
tar -xzf /tmp/dumpling.tar.gz -C /tmp/dumpling-bin dumpling
chmod 700 /tmp/dumpling-bin/dumpling
export DUMPLING_PASSWORD="$(printf '%s' "$DB_PASSWORD_B64" | base64 --decode)"
test -n "$DUMPLING_PASSWORD"
exec /tmp/dumpling-bin/dumpling \
  --host "$TIDB_HOST" \
  --port "$TIDB_PORT" \
  --user "$TIDB_USER" \
  --password "$DUMPLING_PASSWORD" \
  --database "$TIDB_DATABASE" \
  --output /backup/export \
  --filetype sql \
  --consistency snapshot \
  --threads 4 \
  --ca /run/secrets/tidb-ca.pem \
  --allow-cleartext-passwords
'@
    $containerScript = $containerScript -replace "`r`n", "`n"
    $containerScriptPath = Join-Path $secretDirectory "dumpling-$guid.sh"
    [IO.File]::WriteAllText(
        $containerScriptPath,
        $containerScript,
        (New-Object Text.UTF8Encoding($false))
    )
    $containerEnvironment = @(
        "DB_PASSWORD_B64=$encodedPassword",
        "TIDB_HOST=$DatabaseHost",
        "TIDB_PORT=$DatabasePort",
        "TIDB_USER=$DatabaseUser",
        "TIDB_DATABASE=$DatabaseName"
    )
    [IO.File]::WriteAllLines(
        $credentialPath,
        $containerEnvironment,
        (New-Object Text.UTF8Encoding($false))
    )

    $packageMount = $packagePath.Replace('\', '/')
    $certificateMount = (Resolve-Path -LiteralPath $CertificatePath).Path.Replace('\', '/')
    $stagingMount = $stagingDirectory.Replace('\', '/')
    $scriptMount = $containerScriptPath.Replace('\', '/')
    $dockerArguments = @(
        'run', '--rm', '--network', 'bridge',
        '--env-file', $credentialPath,
        '--mount', "type=bind,source=$packageMount,target=/tmp/dumpling.tar.gz,readonly",
        '--mount', "type=bind,source=$certificateMount,target=/run/secrets/tidb-ca.pem,readonly",
        '--mount', "type=bind,source=$scriptMount,target=/run/secrets/dumpling-backup.sh,readonly",
        '--mount', "type=bind,source=$stagingMount,target=/backup",
        $dockerImage, 'sh', '/run/secrets/dumpling-backup.sh'
    )
    & $dockerPath @dockerArguments
    $dumpExitCode = $LASTEXITCODE
    if ($dumpExitCode -ne 0) {
        throw "TiDB Dumpling failed with exit code $dumpExitCode; no database success marker was written."
    }

    $dumpFiles = @(Get-ChildItem -LiteralPath $exportDirectory -File -Recurse -ErrorAction Stop)
    if ($dumpFiles.Count -eq 0 -or
        -not ($dumpFiles | Where-Object { $_.Extension -eq '.sql' } | Select-Object -First 1)) {
        throw 'TiDB Dumpling returned success but produced no SQL dump files.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory(
        $exportDirectory,
        $temporaryArchive,
        [IO.Compression.CompressionLevel]::Optimal,
        $false
    )
    $zip = [IO.Compression.ZipFile]::OpenRead($temporaryArchive)
    try {
        if ($zip.Entries.Count -eq 0) {
            throw 'The database backup archive is empty.'
        }
    } finally {
        $zip.Dispose()
    }

    Move-Item -LiteralPath $temporaryArchive -Destination $publishedArchive
    $checksum = (Get-FileHash -LiteralPath $publishedArchive -Algorithm SHA256).Hash
    [IO.File]::WriteAllText($checksumTemporary, $checksum, [Text.Encoding]::ASCII)
    Move-Item -LiteralPath $checksumTemporary -Destination $checksumFile
    $status = @{
        completedAt = [DateTime]::UtcNow.ToString('o')
        artifact = (Resolve-Path -LiteralPath $publishedArchive).Path
        sha256 = $checksum
    } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($statusTemporary, $status, (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $statusTemporary -Destination $StatusFile
}
catch {
    Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $temporaryArchive
    Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $publishedArchive
    Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $checksumTemporary
    Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $checksumFile
    Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $statusTemporary
    throw
}
finally {
    if ($credentialPath) {
        Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $credentialPath
    }
    if ($containerScriptPath) {
        Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $containerScriptPath
    }
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue -LiteralPath $stagingDirectory
}

$retentionHelper = Join-Path $PSScriptRoot 'backup-retention.ps1'
. $retentionHelper
try {
    $removed = Remove-ExpiredBackups -Directory $OutputDirectory `
        -NamePattern '^database-\d{8}-\d{6}\.zip$' -RetentionDays $RetentionDays
    if ($removed -gt 0) {
        Write-Output "Removed $removed database backup(s) older than $RetentionDays days."
    }
} catch {
    Write-Warning "Database backup succeeded, but expired-backup cleanup failed: $($_.Exception.Message)"
}
Write-Output "TiDB database backup created and checksummed: $publishedArchive"
