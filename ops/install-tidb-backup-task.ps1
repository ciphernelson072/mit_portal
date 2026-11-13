[CmdletBinding()]
param(
    [switch]$Uninstall,
    [switch]$UploadOnly,
    [ValidateRange(0, 23)]
    [int]$Hour = 2,
    [ValidateRange(0, 59)]
    [int]$Minute = 15
)

$ErrorActionPreference = 'Stop'
$taskName = if ($UploadOnly) {
    'MIT Student Portal Upload Backups'
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
if ($UploadOnly) {
    $runnerArguments += ' -UploadOnly'
}
$action = New-ScheduledTaskAction `
    -Execute $powershellPath `
    -Argument $runnerArguments `
    -WorkingDirectory $projectRoot
$trigger = New-ScheduledTaskTrigger -Daily -At ([DateTime]::Today.AddHours($Hour).AddMinutes($Minute))
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
    Write-Output 'Remove it with: .\ops\install-tidb-backup-task.ps1 -Uninstall'
}
