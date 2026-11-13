function New-MySqlCredentialOptionFile {
  param([switch]$AllowEmptyPassword)

  if (-not $env:DB_PASSWORD -and -not $AllowEmptyPassword) {
    throw "Set DB_PASSWORD in the process environment."
  }
  if ($env:DB_PASSWORD -and ($env:DB_PASSWORD.Contains("`r") -or $env:DB_PASSWORD.Contains("`n"))) {
    throw "DB_PASSWORD must not contain line breaks."
  }

  $directory = Join-Path $env:TEMP ("mysql-client-" + [guid]::NewGuid().ToString("N"))
  New-Item -ItemType Directory -Path $directory | Out-Null

  try {
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = Get-Acl -LiteralPath $directory
    $acl.SetAccessRuleProtection($true, $false)
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
      $identity,
      [System.Security.AccessControl.FileSystemRights]::FullControl,
      [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
        [System.Security.AccessControl.InheritanceFlags]::ObjectInherit,
      [System.Security.AccessControl.PropagationFlags]::None,
      [System.Security.AccessControl.AccessControlType]::Allow
    )
    $acl.SetAccessRule($rule)
    Set-Acl -LiteralPath $directory -AclObject $acl

    $optionFile = Join-Path $directory "client.cnf"
    $contents = "[client]`r`n"
    if ($env:DB_PASSWORD) {
      $escapedPassword = $env:DB_PASSWORD.Replace('\', '\\').Replace('"', '\"')
      $contents += "password=`"$escapedPassword`"`r`n"
    }
    [System.IO.File]::WriteAllText($optionFile, $contents, [System.Text.UTF8Encoding]::new($false))
    return $optionFile
  }
  catch {
    Remove-Item -LiteralPath $directory -Recurse -Force -ErrorAction SilentlyContinue
    throw
  }
}

function Remove-MySqlCredentialOptionFile {
  param([Parameter(Mandatory = $true)][string]$OptionFile)

  $directory = Split-Path -Parent $OptionFile
  if (Test-Path -LiteralPath $directory -PathType Container) {
    Remove-Item -LiteralPath $directory -Recurse -Force
  }
}
