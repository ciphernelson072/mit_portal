# Running this project in IntelliJ IDEA

This project needs a couple of one-time IDE settings beyond a normal Maven import -
skipping them is the single most common reason a Spring Boot + Lombok project shows
red errors in IntelliJ even though `mvn compile` works fine from the terminal. Follow
this in order the first time you open the project.

## 1. Prerequisites

- **JDK 21** (`java.version` is 21 in `pom.xml`). Temurin 21 is the deployment baseline.
- **A MySQL (or MariaDB) server** running locally, if you want to run the *application*.
  You do **not** need this to run the test suite - see step 5.
- IntelliJ IDEA Community or Ultimate, either works.

## 2. Open the project

`File → Open...` and select the project's root folder (the one containing `pom.xml`).
IntelliJ detects the Maven project automatically and starts importing dependencies -
let that finish (watch the progress bar in the bottom right) before doing anything else.

If it doesn't auto-import: right-click `pom.xml` → **Add as Maven Project**, or open the
Maven tool window (right edge of the IDE) and click the refresh icon.

## 3. Install the Lombok plugin (do this before anything looks "broken")

This project's DTOs use Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`) to
generate getters/setters/constructors at compile time. Maven already knows how to do
this (Lombok is a normal dependency in `pom.xml`), but **IntelliJ's own code analysis
doesn't understand Lombok until you install its plugin** - without it, you'll see
"cannot find symbol" red errors on things like `gradeDto.setGradeValue(...)` even
though the project builds fine on the command line.

1. `Settings/Preferences → Plugins → Marketplace`, search **"Lombok"**, install it
   (published by Michail Plushnikov), restart the IDE when prompted.
2. `Settings/Preferences → Build, Execution, Deployment → Compiler → Annotation
   Processors` → check **"Enable annotation processing"**.

Both steps are required - the plugin alone isn't enough without annotation processing
enabled, and vice versa.

## 4. Verify the Project SDK

`File → Project Structure → Project` → **SDK** should be a JDK 21 install. If
IntelliJ picked something else (or nothing), set it here and also check
`Project Structure → Modules → Language level` is 21.

## 5. Run the test suite first - it doesn't need MySQL

`src/test/resources/application.properties` points the test context at an in-memory
H2 database instead of the real MySQL datasource, so you can confirm everything is
wired up correctly (Lombok included) before touching a real database at all:

Right-click `SisApplicationTests` → **Run**. A green check means: the full Spring
context loads, every bean wires up, and the demo data seeds correctly - a solid signal
the project is in good shape before you configure a real database.

## 6. Configure the real database (to actually run the app)

Do not put local credentials in tracked property files. The default `dev` profile
uses Laragon MySQL at `127.0.0.1:3306`, database `sis_db`, user `root`, and an empty
password. Create the database and ensure the configured user can run Flyway
migrations. For an existing non-empty development database, the dev profile
baselines it at V1 and applies later migrations; back up the database before the
first startup after updating the application.

If Laragon uses different values, add `DB_URL`, `DB_USER`, and `DB_PASSWORD` to the
`SisApplication` Run Configuration's **Environment variables** field. Keep the
password only in that local IDE configuration; do not commit it. Set `UPLOAD_DIR`
there as well if the default `./uploads/` path is not suitable or writable on your
machine.

## 7. Run it

Right-click `SisApplication` (`src/main/java/com/sis/SisApplication.java`) → **Run**.
By default, sample data is disabled. For a disposable local test database only, set
`SAMPLE_DATA_ENABLED=true` in the IDEA run configuration; see the **Demo Credentials**
section in `README.md` for the test logins. Do not enable sample data against real or
shared institutional data. Then open **http://localhost:8080/** in a browser and sign
in.

## 8. Test from devices on a private Wi-Fi network or MiFi

Use IntelliJ IDEA for ordinary loopback testing and Laragon for local MySQL. For
device testing, start Spring Boot with `.\run-sis.bat run 192.168.1.25` from
PowerShell, replacing the example IP with the current Wi-Fi IPv4, instead of
setting `SERVER_ADDRESS` directly in IDEA. The launcher
checks that the address belongs to a local IPv4 interface, the Windows network
profile is **Private**, and `DB_URL` points to a local `_test` database. It also
requires `UPLOAD_DIR` to be exactly the project's `uploads_wifi_test` directory
and adds only that exact HTTP origin to the CORS allow-list. Set `UPLOAD_DIR`
alongside `DB_URL`, `DB_USER`, and `DB_PASSWORD` in PowerShell before launch.
This guard cannot protect
a separate IDEA run configuration that directly sets `SERVER_ADDRESS`; keep
IDEA loopback-only while the LAN test is running.

Before starting, create and configure the disposable database as described in
the **LAN presentation deployment** section of the root README. Do not point
LAN testing at `sis_db` or any shared/institutional data.
For student test accounts, use the public admissions form and have an
administrator accept and enroll each applicant; only then is a portal login
created. If email delivery is configured, the student receives the generated
temporary password and username by email; otherwise, the administrator receives
a one-time display of the credentials for private handoff. The student signs in
at `/index.html` and must choose a new password before opening the portal.
Never share permanent passwords. Email credentials must be configured privately
in the local environment file, never committed or shared in chat.

For a MiFi, connect both the computer running IDEA and the test device to the same
MiFi. Find the computer's address on that adapter with
`Get-NetIPAddress -AddressFamily IPv4`; use the MiFi adapter's address, not
`127.0.0.1`, `localhost`, a VPN address, or the MiFi router's gateway address.
The test device opens `http://<LAN-IP>:8080/index.html`; `localhost` on that device
points back to the device itself. Internet access is not required for this same-MiFi
test.

Only use LAN testing on a trusted, password-protected network whose Windows network
profile is **Private**. A MiFi is not automatically safe just because it is private:
confirm only trusted test devices are connected. If Windows classifies it as **Public**,
keep the server loopback-only; do not switch the profile or add a firewall rule until
you have verified that it is your trusted MiFi network.
If needed, allow inbound TCP port 8080 only on that Private firewall profile; do not
disable the firewall or create a router port-forward. Before enabling the LAN binding,
use a disposable test database, not the shared `sis_db`, and confirm MySQL is not
reachable from other devices (Laragon can listen on all interfaces even when the
application connects to `127.0.0.1`). Do not add an inbound firewall rule for MySQL
ports 3306 or 33060. Devices on Wi-Fi should open
`http://<LAN-IP>:8080/index.html` (staff use `/admin-login.html`). For a quick
reachability check, run `.\ops\verify-lan-portal.ps1 -HostAddress 192.168.1.25`
with the actual LAN IP from the project root. A working LAN page does not make the development profile suitable for
public exposure; use production configuration, HTTPS, and deployment review before
going online.

## Troubleshooting

**Red squiggles on Lombok-generated methods, but `mvn compile` works fine** →
you're missing step 3 (plugin installed but annotation processing not enabled, or
vice versa - both are required).

**"Cannot resolve symbol" on an entire class** → the Maven import hasn't finished, or
failed. Open the Maven tool window and look for red text; re-run **Reload All Maven
Projects** (the circular-arrow icon).

**App fails to start with a datasource/connection error** → MySQL isn't running, or
the credentials in `application.properties` don't match. The test suite (step 5)
doesn't touch MySQL at all, so use that first to confirm the *code* is fine
independent of your database setup.

**`Table 'sis_db.xxx' doesn't exist` at startup** → confirm Flyway is enabled,
the MySQL user can create and alter tables, and the migration log completed. For
a new install, create the empty `sis_db` database first; do not manually import
`schema.sql` for a clean Flyway-managed database.
