[CmdletBinding()]
param(
    [ValidateSet('gmail-smtp', 'resend')]
    [string]$Provider = 'gmail-smtp',
    [ValidatePattern('^[A-Za-z0-9.!#$%&''*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$')]
    [string]$GmailSender = 'nelliedelson@gmail.com',
    [string]$TestRecipient,
    [switch]$UseEnvironmentCredential,
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,
    [ValidateRange(60, 900)]
    [int]$StartupTimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$environmentPath = Join-Path $projectRoot '.env.cloudflare-tidb'
$taskName = 'MIT Student Portal Cloudflare TiDB'

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw 'The private .env.cloudflare-tidb file is missing. Complete the TiDB pilot setup first.'
}
if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
    throw "The '$taskName' task is not installed. Install the portal task before configuring email."
}

$senderAddress = $GmailSender.Trim()
if ($Provider -eq 'resend') {
    $senderDomain = (Read-Host 'Institution-owned, Resend-verified sending domain (domain only)').Trim().ToLowerInvariant()
    if ($senderDomain -notmatch '^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$') {
        throw 'Enter a valid domain that the institution owns and has verified in Resend.'
    }

    $senderAddress = (Read-Host "Verified sender email address (for example, admissions@$senderDomain)").Trim()
    try {
        $sender = [System.Net.Mail.MailAddress]::new($senderAddress)
    } catch {
        throw 'Enter a valid sender email address on the verified domain.'
    }
    if ($sender.Address -cne $senderAddress -or $sender.Host.ToLowerInvariant() -ne $senderDomain) {
        throw 'The sender email address must use the exact Resend-verified domain.'
    }
} else {
    if (-not $UseEnvironmentCredential) {
        Start-Process 'https://myaccount.google.com/apppasswords'
        Write-Output "Gmail SMTP sender: $senderAddress"
        Write-Output 'Create a fresh App Password named MIT Student Portal SMTP. Do not reuse an App Password shown in or shared through a screenshot.'
    }
}

$recipientPrompt = if ($Provider -eq 'gmail-smtp') {
    "Inbox for the real test email (press Enter to use $senderAddress)"
} else {
    'Inbox address where you can receive the real test email'
}
$recipientAddress = if (-not [string]::IsNullOrWhiteSpace($TestRecipient)) {
    $TestRecipient.Trim()
} else {
    (Read-Host $recipientPrompt).Trim()
}
if ([string]::IsNullOrWhiteSpace($recipientAddress) -and $Provider -eq 'gmail-smtp') {
    $recipientAddress = $senderAddress
}
try {
    $recipient = [System.Net.Mail.MailAddress]::new($recipientAddress)
} catch {
    throw 'Enter a valid inbox address for the test email.'
}
if ($recipient.Address -cne $recipientAddress) {
    throw 'Enter only the test recipient email address.'
}

$mailSecret = $null
$messageId = $null
if ($Provider -eq 'gmail-smtp') {
    if ($UseEnvironmentCredential) {
        foreach ($line in [System.IO.File]::ReadAllLines($environmentPath)) {
            if ($line -match '^MAIL_PASSWORD=(.*)$') {
                $mailSecret = ($matches[1].Trim().Trim('"').Trim("'")) -replace '\s', ''
            }
        }
        if ([string]::IsNullOrWhiteSpace($mailSecret) -or $mailSecret -match '(?i)replace-with|your[_-]') {
            $mailSecret = $null
            throw 'MAIL_PASSWORD is blank or still a placeholder in the private environment file.'
        }
    } else {
        $secureCredential = Read-Host 'Paste the Google App Password (not your Google account password; input is hidden)' -AsSecureString
        $credentialPointer = [IntPtr]::Zero
        try {
            $credentialPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureCredential)
            $mailSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($credentialPointer) -replace '\s', ''
        } finally {
            if ($credentialPointer -ne [IntPtr]::Zero) {
                [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($credentialPointer)
            }
            $secureCredential.Dispose()
        }
        $secureCredential = $null
    }
    if ([string]::IsNullOrWhiteSpace($mailSecret) -or $mailSecret.Length -ne 16) {
        $mailSecret = $null
        throw 'Google App Passwords should be 16 characters. Create a fresh App Password and enter it in the private environment file or at the hidden prompt.'
    }

    $smtpClient = $null
    $testMessage = $null
    try {
        $smtpClient = [System.Net.Mail.SmtpClient]::new('smtp.gmail.com', 587)
        $smtpClient.EnableSsl = $true
        $smtpClient.Timeout = 30000
        $smtpClient.Credentials = [System.Net.NetworkCredential]::new($senderAddress, $mailSecret)
        $testMessage = [System.Net.Mail.MailMessage]::new()
        $testMessage.From = [System.Net.Mail.MailAddress]::new($senderAddress, 'MIT Student Portal')
        [void]$testMessage.To.Add($recipientAddress)
        $testMessage.Subject = 'MIT Student Portal email setup test'
        $testMessage.Body = "This test confirms that the MIT Student Portal can send mail from $senderAddress. No password or sign-in credential is included."
        $smtpClient.Send($testMessage)
        $messageId = 'accepted by Gmail SMTP'
    } catch {
        $mailSecret = $null
        throw 'Gmail SMTP did not accept the test message. Confirm 2-Step Verification is enabled, use a Google App Password (not your account password), and check that outbound SMTP port 587 is allowed. Mail settings were not changed.'
    } finally {
        if ($testMessage) {
            $testMessage.Dispose()
        }
        if ($smtpClient) {
            $smtpClient.Dispose()
        }
    }
} else {
    $secureCredential = Read-Host 'Paste the new, send-only Resend API key (input is hidden)' -AsSecureString
    $credentialPointer = [IntPtr]::Zero
    try {
        $credentialPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureCredential)
        $mailSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($credentialPointer)
    } finally {
        if ($credentialPointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($credentialPointer)
        }
        $secureCredential.Dispose()
    }
    $secureCredential = $null
    if ([string]::IsNullOrWhiteSpace($mailSecret) -or $mailSecret -notmatch '^re_[A-Za-z0-9_-]{20,}$') {
        $mailSecret = $null
        throw 'The supplied key does not look like a Resend API key. Create a new send-only key and try again.'
    }

    $payload = @{
        from    = "MIT Student Portal <$senderAddress>"
        to      = @($recipientAddress)
        subject = 'MIT Student Portal email setup test'
        text    = "This test confirms that the MIT Student Portal can send mail from $senderAddress using the configured Resend account. No password or sign-in credential is included."
    }
    $json = $payload | ConvertTo-Json -Depth 4
    $headers = @{ Authorization = "Bearer $mailSecret" }
    try {
        $providerResponse = Invoke-RestMethod `
            -Uri 'https://api.resend.com/emails' `
            -Method Post `
            -Headers $headers `
            -ContentType 'application/json' `
            -Body $json `
            -TimeoutSec 30
        if ([string]::IsNullOrWhiteSpace([string]$providerResponse.id)) {
            throw 'Resend returned no message ID.'
        }
        $messageId = [string]$providerResponse.id
    } catch {
        $statusCode = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        $mailSecret = $null
        $headers = $null
        $json = $null
        if ($statusCode -eq 401 -or $statusCode -eq 403) {
            throw "Resend rejected the key or permissions (HTTP $statusCode). Confirm the key is a new send-enabled key. The private app settings were not changed."
        }
        if ($statusCode -eq 422) {
            throw 'Resend rejected the sender or recipient. Confirm the sending domain is verified, the sender matches it exactly, and the recipient is permitted. The private app settings were not changed.'
        }
        if ($statusCode -gt 0) {
            throw "Resend did not accept the test email (HTTP $statusCode). The private app settings were not changed."
        }
        throw 'Resend could not be reached or the API key was invalid. The private app settings were not changed.'
    }
}

$headers = $null
$json = $null

if ($Provider -eq 'gmail-smtp') {
    $mailValues = [ordered]@{
        MAIL_ENABLED          = 'true'
        MAIL_PROVIDER         = 'smtp'
        MAIL_API_KEY          = ''
        MAIL_FROM_EMAIL       = $senderAddress
        MAIL_FROM_NAME        = 'MIT Portal'
        MAIL_HOST             = 'smtp.gmail.com'
        MAIL_PORT             = '587'
        MAIL_USERNAME         = $senderAddress
        MAIL_PASSWORD         = $mailSecret
        MAIL_STARTTLS_ENABLE  = 'true'
        MAIL_STARTTLS_REQUIRED = 'true'
        MAIL_SMTP_SSL_ENABLE  = 'false'
    }
} else {
    $mailValues = [ordered]@{
        MAIL_ENABLED          = 'true'
        MAIL_PROVIDER         = 'resend'
        MAIL_API_KEY          = $mailSecret
        MAIL_FROM_EMAIL       = $senderAddress
        MAIL_FROM_NAME        = 'MIT Portal'
        MAIL_HOST             = ''
        MAIL_PORT             = '587'
        MAIL_USERNAME         = ''
        MAIL_PASSWORD         = ''
        MAIL_STARTTLS_ENABLE  = 'true'
        MAIL_STARTTLS_REQUIRED = 'true'
        MAIL_SMTP_SSL_ENABLE  = 'false'
    }
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalAddress -eq '127.0.0.1' } |
    Select-Object -First 1
$applicationPid = $null
if ($listener) {
    $applicationPid = $listener.OwningProcess
    $application = Get-CimInstance Win32_Process -Filter "ProcessId = $applicationPid"
    if ($application.Name -ne 'java.exe' -or $application.CommandLine -notmatch 'com\.sis\.SisApplication') {
        $mailSecret = $null
        $mailValues.MAIL_API_KEY = ''
        $mailValues.MAIL_PASSWORD = ''
        throw "Port $Port is occupied by a process that is not the Student Portal. No app settings were changed."
    }
}

$originalLines = [System.IO.File]::ReadAllLines($environmentPath)
$writtenNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$updatedLines = [System.Collections.Generic.List[string]]::new()
foreach ($line in $originalLines) {
    if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=') {
        $name = $matches[1]
        if ($mailValues.Contains($name)) {
            if ($writtenNames.Add($name)) {
                $updatedLines.Add("$name=$($mailValues[$name])")
            }
            continue
        }
    }
    $updatedLines.Add($line)
}
foreach ($name in $mailValues.Keys) {
    if ($writtenNames.Add($name)) {
        $updatedLines.Add("$name=$($mailValues[$name])")
    }
}

$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($environmentPath, $updatedLines, $utf8WithoutBom)
$mailSecret = $null
$mailValues.MAIL_API_KEY = $null
$mailValues.MAIL_PASSWORD = $null
$updatedLines.Clear()
$originalLines = $null

$taskWasRunning = (Get-ScheduledTask -TaskName $taskName).State -eq 'Running'
if ($applicationPid -and (Get-Process -Id $applicationPid -ErrorAction SilentlyContinue)) {
    Stop-Process -Id $applicationPid -ErrorAction Stop
}
if ($taskWasRunning) {
    $taskDeadline = (Get-Date).AddSeconds(120)
    do {
        Start-Sleep -Seconds 2
        $taskState = (Get-ScheduledTask -TaskName $taskName).State
    } while ($taskState -eq 'Running' -and (Get-Date) -lt $taskDeadline)
    if ($taskState -eq 'Running') {
        throw "The portal process stopped, but the startup task did not finish cleanup. Mail settings were saved; restart '$taskName' after resolving the task."
    }
}
if (-not $taskWasRunning) {
    Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -eq 'cloudflared.exe' -and
            $_.CommandLine -match "tunnel --url http://127\.0\.0\.1:$Port"
        } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }
}

$remainingListener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalAddress -eq '127.0.0.1' } |
    Select-Object -First 1
if ($remainingListener) {
    throw "The old portal still owns port $Port (PID $($remainingListener.OwningProcess)). Mail settings were saved; no second instance was started."
}

Start-ScheduledTask -TaskName $taskName
$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health/readiness" -TimeoutSec 5
        if ($health.status -eq 'UP') {
            $ready = $true
            break
        }
    } catch {
        # Startup may take several minutes while the production context warms up.
    }
}
if (-not $ready) {
    throw "$Provider accepted test message $messageId, and the private settings were saved, but the portal did not become ready. Check the private logs under LocalAppData\MITPortal\logs."
}

$publicUrlFile = Join-Path $env:LOCALAPPDATA 'MITPortal\portal-url.txt'
$publicOrigin = if (Test-Path -LiteralPath $publicUrlFile) {
    (Get-Content -LiteralPath $publicUrlFile -Raw).Trim()
} else {
    $null
}
if (-not $publicOrigin) {
    throw "Mail is configured and the local app is ready, but no public tunnel URL was recorded. Check the private startup logs under LocalAppData\MITPortal\logs."
}

Write-Output "$Provider accepted the test email to $recipientAddress ($messageId). Check that inbox and its spam folder to confirm receipt."
Write-Output "Email is enabled in the private portal configuration. Local readiness: UP."
Write-Output "Portal origin: $publicOrigin"
Write-Output 'New application receipts and post-enrollment one-time password setup links will use the configured email provider.'
