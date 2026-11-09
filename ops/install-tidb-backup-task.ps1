[CmdletBinding()]
param(
    [switch]$Uninstall,
    [switch]$UploadOnly,
    [switch]$Weekly,
    [ValidateSet('Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday')]
    [string]$DayOfWeek = 'Sunday',
    [string]$BackupDirectory = (Join-Path $env:LOCALAPPDATA 'MITPortal\backups'),
    [ValidateRange(0, 23)]
    [int]$Hour = 2,
    [ValidateRange(0, 59)]
    [int]$Minute = 15
)

$ErrorActionPreference = 'Stop'
$customBackupDirectory = $BackupDirectory -ne (Join-Path $env:LOCALAPPDATA 'MITPortal\backups')
$taskName = if ($UploadOnly) {
    'MIT Student Portal Upload Backups'
} elseif ($customBackupDirectory) {
    'MIT Student Portal Local Folder Backups'
} else {
    'MIT Student Portal TiDB and Upload Backups'
}
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$environmentPath = Join-Path $projectRoot '.env.cloudflare-tidb'

if ($Uninstall) {
    $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existingTask) {
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
        Write-Output "Removed scheduled task '$taskName'."
    } else {
        Write-Output "Scheduled task '$taskName' was not installed."
    }
    return
}

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "The private TiDB environment file is missing: $environmentPath"
}

$scriptPath = Join-Path $PSScriptRoot 'run-tidb-backups.ps1'
$powershellPath = Join-Path $PSHOME 'powershell.exe'
$quotedScriptPath = '"' + $scriptPath + '"'
$runnerArguments = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File $quotedScriptPath"
if ($BackupDirectory.Contains('"') -or $BackupDirectory.Contains("`r") -or $BackupDirectory.Contains("`n")) {
    throw 'BackupDirectory must not contain quotes or line breaks.'
}
if ($customBackupDirectory) {
    $runnerArguments += ' -BackupDirectory "' + $BackupDirectory + '"'
}
if ($Weekly) {
    $runnerArguments += ' -RetentionDays 30'
}
if ($UploadOnly) {
    $runnerArguments += ' -UploadOnly'
}
$action = New-ScheduledTaskAction `
    -Execute $powershellPath `
    -Argument $runnerArguments `
    -WorkingDirectory $projectRoot
$runAt = [DateTime]::Today.AddHours($Hour).AddMinutes($Minute)
$trigger = if ($Weekly) {
    New-ScheduledTaskTrigger -Weekly -DaysOfWeek $DayOfWeek -At $runAt
} else {
    New-ScheduledTaskTrigger -Daily -At $runAt
}
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -MultipleInstances IgnoreNew `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 15) `
    -ExecutionTimeLimit ([TimeSpan]::FromHours(2))

Register-ScheduledTask -TaskName $taskName `
    -Action $action `
    -Trigger $trigger `
    -Principal $principal `
    -Settings $settings `
    -Description $(if ($UploadOnly) {
        'Creates checksum-verified local portal upload backups without connecting to TiDB.'
    } elseif ($customBackupDirectory -and $Weekly) {
        'Creates weekly checksum-verified TiDB and portal upload backups in an access-restricted local folder with 30-day retention.'
    } else {
        'Creates checksum-verified local TiDB and portal upload backups; uses the private pilot environment and never logs database credentials.'
    }) `
    -Force | Out-Null

Write-Output "Installed '$taskName' for $currentUser at $($trigger.StartBoundary)."
if ($UploadOnly) {
    Write-Output 'The task runs as the signed-in user and keeps upload backup status fresh without attempting a TiDB export.'
    Write-Output 'Remove it with: .\ops\install-tidb-backup-task.ps1 -UploadOnly -Uninstall'
} else {
    Write-Output 'The task runs as the signed-in user, uses TLS identity verification for TiDB, and retries failed runs.'
    if ($customBackupDirectory -and $Weekly) {
        Write-Output "Backups are stored in: $BackupDirectory"
        Write-Output "The weekly trigger runs every $DayOfWeek at $('{0:D2}:{1:D2}' -f $Hour, $Minute); retention is 30 days."
        Write-Output 'Remove it with: .\ops\install-tidb-backup-task.ps1 -BackupDirectory "<same folder>" -Weekly -Uninstall'
    } else {
        Write-Output 'Remove it with: .\ops\install-tidb-backup-task.ps1 -Uninstall'
    }
}
