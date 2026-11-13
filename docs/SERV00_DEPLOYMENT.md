# Serv00 deployment preparation

This guide deploys the Spring Boot API and static frontend together as a Java
17 executable JAR behind a Serv00 proxy website. It is intended for an account
owner who can configure the Serv00 panel and log in over SSH. It does not
deploy anything or require storing account credentials in this repository.

The Serv00 documentation lists OpenJDK 17, MySQL, reserved TCP ports, proxy
websites, SSL certificates, Binexec, and reboot cron jobs. Confirm that the
specific free account can use each service and has enough process memory and
disk for the application before migrating any data. Serv00's MySQL hostname
depends on the assigned server number (`mysql0.serv00.com`,
`mysql1.serv00.com`, etc.).

## 1. Prepare the deployment package on Windows

From the project root:

```powershell
.\ops\prepare-serv00.ps1
```

The script runs Maven `verify` with the `serv00` profile, confirms the packaged
JAR contains neither the optional `SampleDataLoader` nor `sample_data.sql`,
then creates:

```text
target\serv00-deployment.zip
```

The automated test suite remains in the source repository and runs during
verification. Test fixtures are not part of the deployment JAR. No database
is cleared or migrated by this packaging step.

Transfer the ZIP using SFTP to the Serv00 account. Do not upload `.env` files,
database dumps, the source test tree, or local uploads.

## 2. Prepare Serv00 services

1. Enable **Binexec** in the Serv00 panel or run `devil binexec on`, then
   disconnect and reconnect the SSH session.
2. Select Java 17 (`java17`); verify it with `java17 -version`.
3. Check currently reserved ports with `devil port list`. Reserve one
   available TCP port in the 1024-64000 range, for example:

   ```sh
   devil port add 35160 TCP "MIT Student Portal"
   ```

   Substitute a port that is actually available in your account.
4. Create a Serv00 MySQL database and application user in the panel or with
   `devil mysql db add DATABASE_NAME DATABASE_USER`. Record the database
   endpoint shown for your server (for example `mysql2.serv00.com` for
   server 2), database name, username, and password privately. Use the
   database account created for the application, never `root`.
5. Add the domain in the Serv00 panel as a **Proxy** website targeting
   `localhost` and the reserved TCP port. The CLI form documented by Serv00
   is `devil www add DOMAIN proxy localhost PORT`.
6. Configure an SSL certificate for the website, then enable the Serv00
   force-HTTPS option. Do not accept user credentials before HTTPS is working.

The Serv00 free account's MySQL access and resource limits are controlled by
Serv00. Check its current terms and panel settings; this app requires the Java
process and MySQL service to be usable together on the assigned account.

## 3. Install the package and private configuration

Example installation (replace the username and paths with the actual account):

```sh
mkdir -p "$HOME/mit-portal" "$HOME/.config/mit-portal" "$HOME/mit-portal-data/uploads"
unzip serv00-deployment.zip -d "$HOME/mit-portal"
chmod 700 "$HOME/mit-portal/portal.sh"
cp "$HOME/mit-portal/serv00.env.example" "$HOME/.config/mit-portal/serv00.env"
chmod 600 "$HOME/.config/mit-portal/serv00.env"
```

Edit `~/.config/mit-portal/serv00.env` using `nano` and replace every
placeholder. Generate different long random values for `JWT_SECRET` and
`PAYMENT_WEBHOOK_SECRET`; don't reuse credentials or signing keys from the
temporary TiDB test deployment. Set:

- `SERVER_PORT` to the port reserved in step 2.
- `DB_URL` to the actual Serv00 MySQL host and database. For example:
  `jdbc:mysql://mysql2.serv00.com:3306/ACCOUNT_DATABASE?serverTimezone=UTC`.
  Follow the database connection/TLS requirements shown for your account.
- `DB_USER` and `DB_PASSWORD` to the new app database user.
- `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` to the same exact HTTPS origin,
  with no path or trailing slash.
- `UPLOAD_DIR` to the persistent uploads directory under the Serv00 home.
- Leave `FLUTTERWAVE_SECRET_KEY` and `FLUTTERWAVE_WEBHOOK_SECRET_HASH` blank to
  keep online checkout disabled. To enable it, configure both from Flutterwave
  test mode and follow [PAYMENT_CONFIGURATION.md](./PAYMENT_CONFIGURATION.md).

Keep `SAMPLE_DATA_ENABLED=false`, `FLYWAY_BASELINE_ON_MIGRATE=false`,
`MAIL_ENABLED=false`, `MFA_ENABLED=false`, and
`INITIAL_ADMIN_BOOTSTRAP_ENABLED=false` except during the one-time first-admin
setup described below. Never copy test/demo account records into this database.

The starter environment config binds Spring Boot to `127.0.0.1` behind the
Serv00 proxy and trusts forwarded client IPs only from loopback. Keep the app
port private; only the HTTPS proxy website should be public.

## 4. Initialize the new database

The first deployment applies the project's Flyway migrations to the new
database. Keep `FLYWAY_BASELINE_ON_MIGRATE=false`. Do not use a database
containing unrelated or valuable records.

For a new empty database, set these values in the private environment file for
one startup only:

```sh
export INITIAL_ADMIN_BOOTSTRAP_ENABLED='true'
export INITIAL_ADMIN_USERNAME='your-new-admin-name'
export INITIAL_ADMIN_EMAIL='your-address@example.com'
export INITIAL_ADMIN_PASSWORD='a-unique-password-of-at-least-12-characters'
```

Start the portal and wait for the health check to pass. Then immediately set
`INITIAL_ADMIN_BOOTSTRAP_ENABLED='false'`, remove the username, email, and
password values from the file, and restart the service. The database bootstrap
claim prevents the initial-admin operation from being used as account recovery.
Use the real admin password only on the HTTPS site and change it if the
deployment owner or policy requires it.

## 5. Start and verify

From the package directory:

```sh
./portal.sh start
./portal.sh status
curl -fsS http://127.0.0.1:35160/actuator/health
```

Use the actual reserved port. Check the public HTTPS domain, administrator
login, password-change flow, and role restrictions with synthetic records.
Review `$HOME/mit-portal/logs/portal.log` for startup or migration errors;
don't paste logs containing personal data or secrets into public channels.

To start after a Serv00 server reboot, add this entry to the account's cron
table (`crontab -e`):

```cron
@reboot /usr/home/REPLACE_WITH_LOGIN/mit-portal/portal.sh start
```

Replace the path with the actual home path. Manual controls:

```sh
./portal.sh stop
./portal.sh restart
./portal.sh status
```

## 6. Backups and operating limits

Set a database and uploads backup schedule before accepting user-submitted
records. Keep backups private, retain a copy outside the Serv00 account, and
test a restore. A backup stored only in the same hosting account does not
protect against account loss or provider-side incidents.

This package is not evidence of institution approval, capacity, uptime, or
semester-readiness. Confirm Serv00's free-plan resource limits, application
process policy, MySQL availability, upload quota, backup retention, and support
expectations. Do not use real student, payment, or identity data until the
institution approves the host and the complete restore and access-control
checks pass.

## Official Serv00 references

- [Java](https://docs.serv00.com/Java/)
- [MySQL](https://docs.serv00.com/MySQL/)
- [Port reservation](https://docs.serv00.com/Port_reservation/)
- [Binexec](https://docs.serv00.com/Binexec/)
- [WWW proxy websites](https://docs.serv00.com/WWW/)
- [SSL](https://docs.serv00.com/SSL/)
- [Cron](https://docs.serv00.com/Cron/)
