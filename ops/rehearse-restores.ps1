[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$UploadArchive,
  [string]$DatabaseBackup,
  [ValidatePattern('^[A-Za-z0-9]+_[A-Za-z0-9]+_restore_rehearsal_[A-Za-z0-9]+$')]
  [string]$DisposableDatabaseName,
  [string]$DatabaseHost = "127.0.0.1",
  [ValidateRange(1, 65535)]
  [int]$DatabasePort = 3306,
  [string]$DatabaseUser = "root",
  [switch]$DropDisposableDatabaseAfterSuccess
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$startedAt = [DateTime]::UtcNow
$rehearsalDirectory = Join-Path ([IO.Path]::GetTempPath()) (
  "sis-restore-rehearsal-" + [guid]::NewGuid().ToString("N")
)
New-Item -ItemType Directory -Path $rehearsalDirectory | Out-Null
$uploadDestination = Join-Path $rehearsalDirectory "uploads"
$databaseResult = "not_requested"
$uploadResult = "failed"
$startedDatabase = $false
$droppedDatabase = $false

try {
  if (-not (Test-Path -LiteralPath $UploadArchive -PathType Leaf)) {
    throw "Upload archive not found: $UploadArchive"
  }
  & (Join-Path $PSScriptRoot "restore-uploads.ps1") `
    -ArchiveFile $UploadArchive `
    -DestinationDirectory $uploadDestination
  $restoredUploadCount = (Get-ChildItem -LiteralPath $uploadDestination -Recurse -File).Count
  $uploadResult = "passed"

  if (-not [string]::IsNullOrWhiteSpace($DatabaseBackup)) {
    if ([string]::IsNullOrWhiteSpace($DisposableDatabaseName)) {
      throw "DisposableDatabaseName is required when DatabaseBackup is supplied."
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
      throw "Set DB_PASSWORD in the shell for the disposable database restore."
    }
    if (-not (Test-Path -LiteralPath $DatabaseBackup -PathType Leaf)) {
      throw "Database backup not found: $DatabaseBackup"
    }
    $mysql = Get-Command mysql -CommandType Application -ErrorAction Stop |
      Select-Object -First 1

    $credentialHelper = Join-Path $PSScriptRoot "mysql-credentials.ps1"
    . $credentialHelper
    $credentialFile = New-MySqlCredentialOptionFile
    try {
      $baseArguments = @(
        "--defaults-extra-file=$credentialFile",
        "--host=$DatabaseHost",
        "--port=$DatabasePort",
        "--user=$DatabaseUser"
      )
      $exists = & $mysql.Source @baseArguments `
        "--batch", "--skip-column-names", "-e",
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$DisposableDatabaseName';"
      if ([int]($exists.Trim()) -ne 0) {
        throw "Disposable database already exists: $DisposableDatabaseName"
      }
      $quotedDatabaseName = "``$DisposableDatabaseName``"
      & $mysql.Source @baseArguments "-e",
        "CREATE DATABASE $quotedDatabaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
      if ($LASTEXITCODE -ne 0) {
        throw "Could not create disposable restore database."
      }
      $startedDatabase = $true

      $env:DB_PASSWORD = $env:DB_PASSWORD
      & (Join-Path $PSScriptRoot "restore-mysql.ps1") `
        -BackupFile $DatabaseBackup `
        -DatabaseHost $DatabaseHost `
        -DatabasePort $DatabasePort `
        -DatabaseName $DisposableDatabaseName `
        -DatabaseUser $DatabaseUser

      $flywayCount = & $mysql.Source @baseArguments `
        "--batch", "--skip-column-names",
        "$DisposableDatabaseName", "-e",
        "SELECT COUNT(*) FROM flyway_schema_history;"
      if ([int]($flywayCount.Trim()) -le 0) {
        throw "Disposable restore did not contain Flyway history."
      }
      $tableCount = & $mysql.Source @baseArguments `
        "--batch", "--skip-column-names",
        "$DisposableDatabaseName", "-e",
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='$DisposableDatabaseName';"
      if ([int]($tableCount.Trim()) -le 0) {
        throw "Disposable restore did not contain application tables."
      }
      $databaseResult = "passed"
      if ($DropDisposableDatabaseAfterSuccess) {
        & $mysql.Source @baseArguments "-e",
          "DROP DATABASE $quotedDatabaseName;"
        if ($LASTEXITCODE -ne 0) {
          throw "Could not remove disposable restore database."
        }
        $droppedDatabase = $true
      }
    }
    finally {
      Remove-MySqlCredentialOptionFile -OptionFile $credentialFile
    }
  }

  $completedAt = [DateTime]::UtcNow
  $evidence = [ordered]@{
    startedAt = $startedAt.ToString("o")
    completedAt = $completedAt.ToString("o")
    uploadResult = $uploadResult
    restoredUploadCount = $restoredUploadCount
    databaseResult = $databaseResult
    disposableDatabaseName = $DisposableDatabaseName
    droppedDatabase = $droppedDatabase
  }
  $evidencePath = Join-Path $rehearsalDirectory "restore-rehearsal.json"
  [IO.File]::WriteAllText(
    $evidencePath,
    ($evidence | ConvertTo-Json -Compress),
    (New-Object Text.UTF8Encoding($false))
  )
  Write-Output "Upload restore rehearsal passed: $uploadDestination"
  if ($databaseResult -eq "passed") {
    Write-Output "Database restore rehearsal passed: $DisposableDatabaseName"
  }
  Write-Output "Evidence written: $evidencePath"
}
catch {
  $uploadResult = if ($uploadResult -eq "passed") { $uploadResult } else { "failed" }
  $databaseResult = if ($databaseResult -eq "passed") { "passed" } else { "failed" }
  throw
}
