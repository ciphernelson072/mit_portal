[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$HostAddress
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:DB_URL) -or
    $env:DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1|\[::1\])(?::\d+)?/([A-Za-z0-9_]+)(?:[?;]|$)' -or
    $Matches[1] -notmatch '(?i)_test$') {
    throw 'Set DB_URL to a disposable MySQL database on this computer whose name ends with _test (for example, sis_wifi_test); shared sis_db and remote databases are blocked.'
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$expectedUploadDirectory = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'uploads_wifi_test'))
if ([string]::IsNullOrWhiteSpace($env:UPLOAD_DIR) -or
    -not [string]::Equals(
        [System.IO.Path]::GetFullPath($env:UPLOAD_DIR),
        $expectedUploadDirectory,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Set UPLOAD_DIR to the isolated test path $expectedUploadDirectory; shared uploads are blocked."
}

$parsedAddress = $null
if (-not [System.Net.IPAddress]::TryParse($HostAddress, [ref]$parsedAddress) -or
    $parsedAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
    throw 'Supply a valid local IPv4 address for the Wi-Fi or MiFi adapter.'
}

$localAddress = Get-NetIPAddress -AddressFamily IPv4 -IPAddress $parsedAddress.IPAddressToString -ErrorAction SilentlyContinue
if ($null -eq $localAddress) {
    throw 'The requested address is not assigned to a local IPv4 interface.'
}

$networkProfile = Get-NetConnectionProfile -InterfaceIndex $localAddress.InterfaceIndex -ErrorAction SilentlyContinue
if ($null -eq $networkProfile -or $networkProfile.NetworkCategory -ne 'Private') {
    throw 'LAN binding requires a Windows network profile classified as Private. Keep the server loopback-only on Public networks.'
}

Write-Output "LAN binding checks passed for $($parsedAddress.IPAddressToString). Use only trusted devices on this Private network."
