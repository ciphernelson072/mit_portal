param(
  [Parameter(Mandatory = $true)][string]$UploadDirectory,
  [string]$OutputDirectory = "./backups",
  [string]$StatusFile,
  [ValidateRange(1, 3650)][int]$RetentionDays = 30
)

$ErrorActionPreference = "Stop"
$source = (Resolve-Path -LiteralPath $UploadDirectory -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $source -PathType Container)) {
  throw "Upload directory is not a directory: $UploadDirectory"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$resolvedOutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory -ErrorAction Stop).Path
$normalizedSource = $source.TrimEnd('\')
$sourcePrefix = "$normalizedSource\"
if ([string]::Equals($resolvedOutputDirectory.TrimEnd('\'), $normalizedSource, [StringComparison]::OrdinalIgnoreCase) -or
    $resolvedOutputDirectory.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Backup output must not be the upload directory or one of its subdirectories."
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$publishedArchive = Join-Path $resolvedOutputDirectory "uploads-$timestamp.zip"
$temporaryArchive = "$publishedArchive.partial"
$checksumFile = "$publishedArchive.sha256"
$checksumTemporary = "$checksumFile.tmp"
if ([string]::IsNullOrWhiteSpace($StatusFile)) {
  $StatusFile = Join-Path $resolvedOutputDirectory ".uploads-backup-success"
}
$statusDirectory = Split-Path -Parent $StatusFile
if ($statusDirectory) {
  New-Item -ItemType Directory -Force -Path $statusDirectory | Out-Null
}
$statusFile = $StatusFile
$statusTemporary = "$statusFile.tmp"

try {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::CreateFromDirectory(
    $source,
    $temporaryArchive,
    [System.IO.Compression.CompressionLevel]::Optimal,
    $false
  )
  if (-not (Test-Path -LiteralPath $temporaryArchive) -or
      (Get-Item -LiteralPath $temporaryArchive).Length -eq 0) {
    throw "Upload archive was not created or is empty."
  }

  $checksum = (Get-FileHash -LiteralPath $temporaryArchive -Algorithm SHA256).Hash
  Move-Item -Force -LiteralPath $temporaryArchive -Destination $publishedArchive
  Set-Content -NoNewline -Encoding ascii -Path $checksumTemporary -Value $checksum
  Move-Item -Force -LiteralPath $checksumTemporary -Destination $checksumFile
  $completedAt = [DateTime]::UtcNow.ToString("o")
  $status = @{
    completedAt = $completedAt
    artifact = (Resolve-Path -LiteralPath $publishedArchive).Path
    sha256 = $checksum
  } | ConvertTo-Json -Compress
  [System.IO.File]::WriteAllText($statusTemporary, $status, [System.Text.UTF8Encoding]::new($false))
  Move-Item -Force -LiteralPath $statusTemporary -Destination $statusFile
}
catch {
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $temporaryArchive
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $publishedArchive
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $checksumTemporary
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $checksumFile
  Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $statusTemporary
  throw
}

$retentionHelper = Join-Path $PSScriptRoot "backup-retention.ps1"
. $retentionHelper
try {
  $removed = Remove-ExpiredBackups -Directory $OutputDirectory `
    -NamePattern '^uploads-\d{8}-\d{6}\.zip$' -RetentionDays $RetentionDays
  if ($removed -gt 0) {
    Write-Output "Removed $removed upload backup(s) older than $RetentionDays days."
  }
}
catch {
  Write-Warning "Upload backup succeeded, but expired-backup cleanup failed: $($_.Exception.Message)"
}
Write-Output "Upload archive created: $publishedArchive"
