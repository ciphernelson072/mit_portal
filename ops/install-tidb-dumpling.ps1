[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$version = '8.5.8'
$expectedSha256 = '9107080DF0BC3D21BD6B96CA4B990B54D46A22F9152E4B9DC4274596B82A7C06'
$toolDirectory = Join-Path $env:LOCALAPPDATA 'MITPortal\tools'
$archivePath = Join-Path $toolDirectory "dumpling-v$version-linux-amd64.tar.gz"
$downloadUrl = "https://tiup-mirrors.pingcap.com/dumpling-v$version-linux-amd64.tar.gz"

New-Item -ItemType Directory -Path $toolDirectory -Force | Out-Null
$identitySid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$icacls = Join-Path $env:SystemRoot 'System32\icacls.exe'
& $icacls $toolDirectory '/inheritance:r' '/grant:r' `
    "*${identitySid}:(OI)(CI)F" `
    '*S-1-5-18:(OI)(CI)F' `
    '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Could not restrict the TiDB tool directory permissions.'
}

if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
    $partialPath = "$archivePath.partial"
    try {
        & curl.exe --fail --location --silent --show-error --retry 2 `
            --output $partialPath $downloadUrl
        if ($LASTEXITCODE -ne 0) {
            throw 'Official Dumpling package download failed.'
        }
        $downloadHash = (Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash
        if ($downloadHash -ne $expectedSha256) {
            throw 'Downloaded Dumpling package SHA-256 does not match the pinned release.'
        }
        Move-Item -LiteralPath $partialPath -Destination $archivePath
    }
    catch {
        Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath $partialPath
        throw
    }
}

$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
if ($archiveHash -ne $expectedSha256) {
    throw 'Installed Dumpling package SHA-256 does not match the pinned release.'
}
$entries = @(& tar.exe -tzf $archivePath)
if ($LASTEXITCODE -ne 0 -or @($entries | Where-Object { $_ -eq 'dumpling' }).Count -ne 1) {
    throw 'Installed Dumpling archive failed validation.'
}

Write-Output "Verified official TiDB Dumpling v$version package at $archivePath."
