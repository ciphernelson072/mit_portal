param(
  [string]$OutputDirectory = "./backups",
  [string]$DatabaseHost = "127.0.0.1",
  [ValidateRange(1, 65535)][int]$DatabasePort = 3306,
  [string]$DatabaseName = "sis_db",
  [string]$DatabaseUser = "root",
  [string]$StatusFile,
  [string]$ArtifactPathPrefix,
  [ValidateSet("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY")]
  [string]$SslMode,
  [string]$SslCa,
  [switch]$AllowEmptyPassword,
  [ValidateRange(1, 3650)][int]$RetentionDays = 30
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$publishedDestination = Join-Path $OutputDirectory "$DatabaseName-$timestamp.sql"
$destination = "$publishedDestination.partial"
$checksumFile = "$publishedDestination.sha256"
$checksumTemporary = "$checksumFile.tmp"
if ([string]::IsNullOrWhiteSpace($StatusFile)) {
  $StatusFile = Join-Path $OutputDirectory ".backup-success"
}
$statusDirectory = Split-Path -Parent $StatusFile
if ($statusDirectory) {
  New-Item -ItemType Directory -Force -Path $statusDirectory | Out-Null
}
$statusFile = $StatusFile
$statusTemporary = "$statusFile.tmp"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

if (-not $env:DB_PASSWORD -and -not $AllowEmptyPassword) {
  throw "Set DB_PASSWORD in the shell before running this backup."
}

$credentialHelper = Join-Path $PSScriptRoot "mysql-credentials.ps1"
. $credentialHelper
$credentialFile = New-MySqlCredentialOptionFile -AllowEmptyPassword:$AllowEmptyPassword
try {
  $dumpArguments = @(
    "--defaults-extra-file=$credentialFile",
    "--host=$DatabaseHost",
    "--user=$DatabaseUser",
    "--port=$DatabasePort"
  )
  if ($SslMode) {
    $dumpArguments += "--ssl-mode=$SslMode"
  }
  if ($SslCa) {
    if (-not (Test-Path -LiteralPath $SslCa -PathType Leaf)) {
      throw "The configured MySQL CA certificate does not exist: $SslCa"
    }
    $dumpArguments += "--ssl-ca=$SslCa"
  }
  $dumpArguments += @(
    '--no-tablespaces',
    '--single-transaction',
    '--routines',
    '--triggers',
    '--events',
    '--hex-blob',
    "--result-file=$destination",
    $DatabaseName
  )
  & mysqldump @dumpArguments
  $dumpExitCode = $LASTEXITCODE
}
finally {
  Remove-MySqlCredentialOptionFile -OptionFile $credentialFile
}

if ($dumpExitCode -ne 0) {
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $destination
  throw "mysqldump failed with exit code $dumpExitCode."
}
if (-not (Test-Path -LiteralPath $destination) -or
    (Get-Item -LiteralPath $destination).Length -eq 0) {
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $destination
  throw "mysqldump did not produce a non-empty backup file."
}
$checksum = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
try {
  Move-Item -Force -LiteralPath $destination -Destination $publishedDestination
  Set-Content -NoNewline -Encoding ascii -Path $checksumTemporary -Value $checksum
  Move-Item -Force -LiteralPath $checksumTemporary -Destination $checksumFile
  $completedAt = [DateTime]::UtcNow.ToString("o")
  $markerArtifact = (Resolve-Path -LiteralPath $publishedDestination).Path
  if (-not [string]::IsNullOrWhiteSpace($ArtifactPathPrefix)) {
    $markerArtifact = $ArtifactPathPrefix.TrimEnd('/') + '/' + [IO.Path]::GetFileName($publishedDestination)
  }
  $status = @{
    completedAt = $completedAt
    artifact = $markerArtifact
    sha256 = $checksum
  } | ConvertTo-Json -Compress
  [System.IO.File]::WriteAllText($statusTemporary, $status, [System.Text.UTF8Encoding]::new($false))
  Move-Item -Force -LiteralPath $statusTemporary -Destination $statusFile
}
catch {
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $destination
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $publishedDestination
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $checksumTemporary
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $checksumFile
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $statusTemporary
  throw
}
$retentionHelper = Join-Path $PSScriptRoot "backup-retention.ps1"
. $retentionHelper
try {
  $removed = Remove-ExpiredBackups -Directory $OutputDirectory `
    -NamePattern ("^" + [regex]::Escape($DatabaseName) + "-\d{8}-\d{6}\.sql$") `
    -RetentionDays $RetentionDays
  if ($removed -gt 0) {
    Write-Output "Removed $removed database backup(s) older than $RetentionDays days."
  }
}
catch {
  Write-Warning "Database backup succeeded, but expired-backup cleanup failed: $($_.Exception.Message)"
}
Write-Output "Backup created: $publishedDestination"
