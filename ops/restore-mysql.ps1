param(
  [Parameter(Mandatory = $true)][string]$BackupFile,
  [string]$DatabaseHost = "127.0.0.1",
  [ValidateRange(1, 65535)][int]$DatabasePort = 3306,
  [string]$DatabaseName = "sis_db",
  [string]$DatabaseUser = "root",
  [switch]$AllowUnverifiedBackup
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) {
  throw "Backup file not found: $BackupFile"
}
if (-not $env:DB_PASSWORD) {
  throw "Set DB_PASSWORD in the shell before restoring."
}
if ((Get-Item -LiteralPath $BackupFile).Length -eq 0) {
  throw "Backup file is empty."
}

$checksumFile = "$BackupFile.sha256"
if (Test-Path -LiteralPath $checksumFile -PathType Leaf) {
  $expectedChecksum = (Get-Content -LiteralPath $checksumFile -Raw).Trim()
  if ($expectedChecksum -notmatch '^[A-Fa-f0-9]{64}$') {
    throw "Backup checksum file is invalid: $checksumFile"
  }
  $actualChecksum = (Get-FileHash -LiteralPath $BackupFile -Algorithm SHA256).Hash
  if (-not [string]::Equals($expectedChecksum, $actualChecksum, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Backup checksum verification failed; restore was not started."
  }
}
elseif (-not $AllowUnverifiedBackup) {
  throw "Backup checksum file not found. Use -AllowUnverifiedBackup only for a trusted legacy backup."
}

$credentialHelper = Join-Path $PSScriptRoot "mysql-credentials.ps1"
. $credentialHelper
$credentialFile = New-MySqlCredentialOptionFile
$process = $null
$processStarted = $false
try {
  $mysqlApplication = Get-Command mysql -CommandType Application -ErrorAction Stop |
    Select-Object -First 1
  $mysqlArguments = @(
    "--defaults-extra-file=$credentialFile"
    "--host=$DatabaseHost"
    "--port=$DatabasePort"
    "--user=$DatabaseUser"
    $DatabaseName
  )
  $mysqlArgumentLine = ($mysqlArguments | ForEach-Object {
    $value = [string]$_
    if ($value.Length -gt 0 -and $value -notmatch '[\s"]') {
      return $value
    }

    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.Append('"')
    $index = 0
    while ($index -lt $value.Length) {
      $backslashCount = 0
      while ($index -lt $value.Length -and $value[$index] -eq [char]'\') {
        $backslashCount++
        $index++
      }

      if ($index -eq $value.Length) {
        for ($slash = 0; $slash -lt (2 * $backslashCount); $slash++) {
          [void]$builder.Append([char]'\')
        }
        break
      }

      if ($value[$index] -eq [char]'"') {
        for ($slash = 0; $slash -lt (2 * $backslashCount + 1); $slash++) {
          [void]$builder.Append([char]'\')
        }
        [void]$builder.Append([char]'"')
      } else {
        for ($slash = 0; $slash -lt $backslashCount; $slash++) {
          [void]$builder.Append([char]'\')
        }
        [void]$builder.Append($value[$index])
      }
      $index++
    }
    [void]$builder.Append('"')
    $builder.ToString()
  }) -join ' '

  $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $processInfo.FileName = $mysqlApplication.Source
  $processInfo.Arguments = $mysqlArgumentLine
  $processInfo.UseShellExecute = $false
  $processInfo.RedirectStandardInput = $true
  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $processInfo
  if (-not $process.Start()) {
    throw "mysql could not be started."
  }
  $processStarted = $true

  $backupStream = [System.IO.File]::OpenRead((Resolve-Path -LiteralPath $BackupFile).Path)
  try {
    $backupStream.CopyTo($process.StandardInput.BaseStream)
  } finally {
    $backupStream.Dispose()
    $process.StandardInput.Close()
  }

  $process.WaitForExit()
  $restoreExitCode = $process.ExitCode
}
finally {
  try {
    if ($processStarted -and -not $process.HasExited) {
      $process.StandardInput.Close()
      $process.WaitForExit()
    }
  } finally {
    if ($process) {
      $process.Dispose()
    }
    Remove-MySqlCredentialOptionFile -OptionFile $credentialFile
  }
}
if ($restoreExitCode -ne 0) {
  throw "mysql restore failed with exit code $restoreExitCode."
}
Write-Output "Restore completed from: $BackupFile"
