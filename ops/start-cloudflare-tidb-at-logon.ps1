[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,
    [ValidateRange(30, 600)]
    [int]$StartupTimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$environmentPath = Join-Path $projectRoot '.env.cloudflare-tidb'
$logDirectory = Join-Path $env:LOCALAPPDATA 'MITPortal\logs'
$stateDirectory = Join-Path $env:LOCALAPPDATA 'MITPortal'
$runCloudflareScript = Join-Path $PSScriptRoot 'run-cloudflare-tidb.ps1'
$cloudflaredCommand = Get-Command 'cloudflared.exe' -ErrorAction SilentlyContinue
if (-not $cloudflaredCommand) {
    $cloudflaredCommand = Get-Command 'cloudflared' -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runLog = Join-Path $logDirectory "startup-$timestamp.log"
$cloudflaredOut = Join-Path $logDirectory "cloudflared-$timestamp.out.log"
$cloudflaredErr = Join-Path $logDirectory "cloudflared-$timestamp.err.log"
$applicationOut = Join-Path $logDirectory "application-$timestamp.out.log"
$applicationErr = Join-Path $logDirectory "application-$timestamp.err.log"
$publicUrlFile = Join-Path $stateDirectory 'portal-url.txt'
$mutex = New-Object System.Threading.Mutex($false, 'Local\MITStudentPortalCloudflareTidb')
$mutexAcquired = $false
$tunnelProcess = $null
$applicationProcess = $null
$applicationListenerProcessId = $null

function Write-RunLog {
    param([string]$Message)
    $line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $runLog -Value $line -Encoding UTF8
}

function Read-LogFileWhileWriting {
    param([string]$Path)

    $stream = [IO.File]::Open(
        $Path,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::ReadWrite
    )
    try {
        $reader = New-Object IO.StreamReader($stream)
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Set-PilotOrigin {
    param(
        [string]$Path,
        [string]$Origin
    )

    $content = [IO.File]::ReadAllText($Path)
    foreach ($name in @('FRONTEND_URL', 'CORS_ALLOWED_ORIGINS')) {
        $pattern = "(?m)^$name=.*$"
        $matches = [regex]::Matches($content, $pattern)
        if ($matches.Count -ne 1) {
            throw "Expected exactly one $name entry in the private pilot environment file."
        }
        $content = [regex]::Replace($content, $pattern, "$name=$Origin")
    }
    [IO.File]::WriteAllText($Path, $content, (New-Object System.Text.UTF8Encoding($false)))
}

try {
    try {
        $mutexAcquired = $mutex.WaitOne(0)
    } catch [Threading.AbandonedMutexException] {
        $mutexAcquired = $true
        Write-RunLog 'Recovered the startup lock after an interrupted prior run.'
    }
    if (-not $mutexAcquired) {
        Write-RunLog 'A portal startup task is already running; this invocation is exiting.'
        exit 0
    }

    if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
        throw 'The private .env.cloudflare-tidb file is missing. Complete the TiDB pilot setup before enabling automatic startup.'
    }
    if (-not $cloudflaredCommand) {
        throw 'cloudflared is not installed or is not available on PATH.'
    }

    $existingListener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -eq '127.0.0.1' }
    if ($existingListener) {
        throw "Port $Port already has a loopback listener. Stop the existing portal before using this automatic startup task."
    }

    Write-RunLog 'Checking the private application and TiDB configuration.'
    & $runCloudflareScript -Port $Port -ValidateOnly

    Write-RunLog 'Starting an account-less Cloudflare Quick Tunnel to the loopback-only application.'
    $tunnelProcess = Start-Process -FilePath $cloudflaredCommand.Source `
        -ArgumentList @('tunnel', '--url', "http://127.0.0.1:$Port", '--no-autoupdate') `
        -RedirectStandardOutput $cloudflaredOut `
        -RedirectStandardError $cloudflaredErr `
        -WindowStyle Hidden `
        -PassThru

    $publicOrigin = $null
    $tunnelDeadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $tunnelDeadline) {
        if ($tunnelProcess.HasExited) {
            throw "cloudflared exited during startup with code $($tunnelProcess.ExitCode). Check its private logs in $logDirectory."
        }
        foreach ($logPath in @($cloudflaredOut, $cloudflaredErr)) {
            if (Test-Path -LiteralPath $logPath) {
                $logContent = Read-LogFileWhileWriting -Path $logPath
                $urlMatch = [regex]::Match($logContent, 'https://[a-z0-9-]+\.trycloudflare\.com', 'IgnoreCase')
                if ($urlMatch.Success) {
                    $publicOrigin = $urlMatch.Value
                    break
                }
            }
        }
        if ($publicOrigin) { break }
        Start-Sleep -Seconds 2
    }
    if (-not $publicOrigin) {
        throw "cloudflared did not report a Quick Tunnel URL within 90 seconds. Check its private logs in $logDirectory."
    }

    Set-PilotOrigin -Path $environmentPath -Origin $publicOrigin
    Set-Content -LiteralPath $publicUrlFile -Value $publicOrigin -Encoding UTF8
    Write-RunLog "Quick Tunnel created at $publicOrigin."

    & $runCloudflareScript -Port $Port -ValidateOnly

    Write-RunLog 'Starting the production-profile Java application.'
    $powershellPath = Join-Path $PSHOME 'powershell.exe'
    $quotedRunScript = '"' + $runCloudflareScript + '"'
    $applicationProcess = Start-Process -FilePath $powershellPath `
        -ArgumentList @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $quotedRunScript, '-Port', [string]$Port) `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $applicationOut `
        -RedirectStandardError $applicationErr `
        -WindowStyle Hidden `
        -PassThru

    $ready = $false
    $applicationDeadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $applicationDeadline) {
        if ($applicationProcess.HasExited) {
            throw "The Java launcher exited during startup with code $($applicationProcess.ExitCode). Check its private logs in $logDirectory."
        }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health/readiness" -TimeoutSec 5
            if ($health.status -eq 'UP') {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 5
        }
    }
    if (-not $ready) {
        throw "The application did not become ready within $StartupTimeoutSeconds seconds. Check its private logs in $logDirectory."
    }
    $applicationListener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -eq '127.0.0.1' } |
        Select-Object -First 1
    if (-not $applicationListener) {
        throw "The application reported ready but no loopback listener was found on port $Port."
    }
    $applicationListenerProcessId = $applicationListener.OwningProcess

    Write-RunLog "Application readiness is UP on 127.0.0.1:$Port."
    $publicReady = $false
    $publicDeadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $publicDeadline) {
        try {
            $response = Invoke-WebRequest -Uri "$publicOrigin/" -TimeoutSec 10 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                $publicReady = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 5
        }
    }
    if ($publicReady) {
        Write-RunLog 'Public HTTPS portal returned HTTP 200.'
    } else {
        Write-RunLog 'Local application is ready, but the public tunnel did not return HTTP 200 yet.'
    }
    Start-Process $publicOrigin
    Write-RunLog "Opened the portal in the default browser. Current URL is saved in $publicUrlFile."

    while (-not $applicationProcess.HasExited) {
        if ($tunnelProcess.HasExited) {
            throw "cloudflared stopped with exit code $($tunnelProcess.ExitCode). Check its private logs in $logDirectory."
        }
        Start-Sleep -Seconds 15
    }
    if ($applicationProcess.ExitCode -ne 0) {
        throw "The Java launcher stopped with exit code $($applicationProcess.ExitCode). Check its private logs in $logDirectory."
    }
    Write-RunLog 'The Java application has stopped.'
} catch {
    Write-RunLog "Startup failed: $($_.Exception.Message)"
    throw
} finally {
    if ($applicationListenerProcessId) {
        $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
            Where-Object {
                $_.LocalAddress -eq '127.0.0.1' -and
                $_.OwningProcess -eq $applicationListenerProcessId
            }
        if ($listener) {
            $listenerProcess = Get-Process -Id $applicationListenerProcessId -ErrorAction SilentlyContinue
            if ($listenerProcess -and $listenerProcess.ProcessName -eq 'java') {
                Stop-Process -Id $applicationListenerProcessId -ErrorAction SilentlyContinue
                Write-RunLog 'Stopped the Java process started by this task.'
            }
        }
    }
    if ($applicationProcess -and -not $applicationProcess.HasExited) {
        Stop-Process -Id $applicationProcess.Id -ErrorAction SilentlyContinue
    }
    if ($tunnelProcess -and -not $tunnelProcess.HasExited) {
        Stop-Process -Id $tunnelProcess.Id -ErrorAction SilentlyContinue
        Write-RunLog 'Stopped the Cloudflare tunnel process.'
    }
    if ($mutexAcquired) {
        $mutex.ReleaseMutex()
    }
    $mutex.Dispose()
}
