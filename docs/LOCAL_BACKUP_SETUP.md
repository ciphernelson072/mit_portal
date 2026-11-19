# Weekly local backup task

The Windows pilot can create checksum-verified TiDB and upload archives directly
in a local folder. The task runs as the signed-in Windows account, uses the
private `.env.cloudflare-tidb` file, retains 30 days, and does not print database
credentials. The destination ACL is restricted to that account, SYSTEM, and
local Administrators.

Install the task with the destination folder you control:

```powershell
.\ops\install-tidb-backup-task.ps1 `
  -BackupDirectory "$env:USERPROFILE\Downloads\MIT BACKUP FOLDER" `
  -Weekly -DayOfWeek Sunday -Hour 3 -Minute 15
```

The scheduled task needs the user to be signed in and Docker Desktop's Linux
engine available. Windows starts a missed task when it next becomes available.
Inspect task status in Task Scheduler under **MIT Student Portal Local Folder
Backups**. Its last-run result must be `0`; the output folder should contain
`database-*.zip` and `uploads-*.zip` archives with matching `.sha256` files.

To remove only this task:

```powershell
.\ops\install-tidb-backup-task.ps1 `
  -BackupDirectory "$env:USERPROFILE\Downloads\MIT BACKUP FOLDER" `
  -Weekly -Uninstall
```

These are local, access-restricted archives, not encrypted archives or an
off-host disaster-recovery copy. Protect the Windows device with full-disk
encryption and retain a separate institution-managed backup before relying on
these files for disaster recovery.
