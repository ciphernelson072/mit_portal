[CmdletBinding()]
param(
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$taskName = 'MIT Student Portal Cloudflare TiDB'
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().Name

if ($Uninstall) {
    $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existingTask) {
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
        Write-Output "Removed the current-user logon task '$taskName'."
    } else {
        Write-Output "The current-user logon task '$taskName' was not installed."
    }
    return
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runnerPath = Join-Path $PSScriptRoot 'start-cloudflare-tidb-at-logon.ps1'
$environmentPath = Join-Path $projectRoot '.env.cloudflare-tidb'
if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "Missing $environmentPath. Complete the private TiDB pilot configuration first."
}

$powershellPath = Join-Path $PSHOME 'powershell.exe'
$quotedRunnerPath = '"' + $runnerPath + '"'
$action = New-ScheduledTaskAction `
    -Execute $powershellPath `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File $quotedRunnerPath -Port 8080 -StartupTimeoutSeconds 600" `
    -WorkingDirectory $projectRoot
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
$principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -MultipleInstances IgnoreNew `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero)
$task = New-ScheduledTask `
    -Action $action `
    -Trigger $trigger `
    -Principal $principal `
    -Settings $settings `
    -Description 'Starts the loopback-only Student Portal Java app and Cloudflare Quick Tunnel after this Windows user signs in.'

Register-ScheduledTask -TaskName $taskName -InputObject $task -Force | Out-Null
Write-Output "Installed '$taskName' for $currentUser. It runs after sign-in without administrator privileges."
Write-Output 'The task uses the private .env.cloudflare-tidb file and opens the portal after local health checks.'
Write-Output 'Remove it with: .\ops\install-cloudflare-tidb-autostart.ps1 -Uninstall'
