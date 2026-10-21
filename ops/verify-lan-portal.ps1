param(
    [int]$Port = 8080,
    [string]$HostAddress = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($HostAddress)) {
    $HostAddress = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -First 1 -ExpandProperty IPAddress)
}

$baseUrl = "http://$HostAddress`:$Port"
$health = Invoke-WebRequest -Uri "$baseUrl/actuator/health/readiness" -UseBasicParsing -TimeoutSec 10
$loginPage = Invoke-WebRequest -Uri "$baseUrl/index.html" -UseBasicParsing -TimeoutSec 10

if ($health.StatusCode -ne 200) { throw "Health endpoint returned $($health.StatusCode)." }
if ($loginPage.StatusCode -ne 200) { throw "Login page returned $($loginPage.StatusCode)." }

Write-Output "LAN portal is reachable: $baseUrl/index.html"
Write-Output "Readiness endpoint: $baseUrl/actuator/health/readiness"
try {
    $aggregate = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 10
    if ($aggregate.StatusCode -ne 200) {
        Write-Warning "Aggregate health is $($aggregate.StatusCode); inspect mail/database contributors before production use."
    }
} catch {
    Write-Warning "Aggregate health is unavailable; readiness and the login page are still reachable."
}
Write-Output "Share this URL only on the trusted presentation network."
