function Remove-ExpiredBackups {
  param(
    [Parameter(Mandatory = $true)][string]$Directory,
    [Parameter(Mandatory = $true)][string]$NamePattern,
    [Parameter(Mandatory = $true)][int]$RetentionDays
  )

  if ($RetentionDays -lt 1) {
    throw "RetentionDays must be at least one."
  }

  $cutoff = [DateTime]::UtcNow.AddDays(-$RetentionDays)
  $expired = Get-ChildItem -LiteralPath $Directory -File |
    Where-Object { $_.Name -match $NamePattern -and $_.LastWriteTimeUtc -lt $cutoff }
  $removed = 0
  foreach ($file in $expired) {
    Remove-Item -LiteralPath $file.FullName -Force
    $checksumFile = "$($file.FullName).sha256"
    if (Test-Path -LiteralPath $checksumFile -PathType Leaf) {
      Remove-Item -LiteralPath $checksumFile -Force
    }
    $removed++
  }
  return $removed
}
