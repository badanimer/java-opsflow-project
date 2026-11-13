# OpsFlow Local Development Environment Setup

> Recorded: 2026-09-12
> Operating system: Windows 11 Home (10.0.26200)
> Note: all middleware is installed as **native Windows services**. Docker Desktop also works (verified on 2026-11-04, see 2.6) and is used for tests; the services themselves have not been moved into containers.

---

## 1. Environment Overview

| Component | Purpose | Version | Installed via | Status |
|---|---|---|---|---|
| JDK | Java runtime and compiler | Microsoft Build of OpenJDK 17 (17.0.20.1) | winget | ✅ Installed |
| Maven | Spring Boot build tool | 3.9.16 | Manual download and extract | ✅ Installed |
| MySQL | Primary database | 8.4.9 | winget (MSI, manual initialization) | ✅ Running as a Windows service, starts on boot |
| Redis | Cache, idempotency, rate limiting | 8.10.1 (redis-windows-fork, MSYS2 build) | winget + NSSM registered as a service | ✅ Running as a Windows service, starts on boot |
| Erlang/OTP | RabbitMQ runtime dependency | 27.3.4.17 | Manual download and install | ✅ Installed |
| RabbitMQ | Message queue (asynchronous tasks) | 4.3.5 | Manual download and install | ✅ Running as a Windows service, starts on boot, management plugin enabled |
| Node.js | Frontend build and runtime | v21.7.0 | Preinstalled | ✅ Installed |
| npm | Frontend package manager | 10.5.0 | Preinstalled | ✅ Installed |
| Git | Version control | 2.44.0.windows.1 | Preinstalled | ✅ Installed |
| Docker Desktop | Container deployment | 4.90.0 | winget | ✅ Works on WSL2 (verified on 2026-11-04), not started with Windows; start Docker Desktop before running the integration tests |

---

## 2. Detailed Configuration

### 2.1 JDK 17

- Install path: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
- Environment variable: `JAVA_HOME` = the path above (user level), added to `PATH`
- Verify:
  ```powershell
  java -version
  ```

### 2.2 Maven 3.9.16

- Install path: `C:\Tools\maven`
- Environment variable: `M2_HOME` = `C:\Tools\maven`, with the `bin` directory added to `PATH`
- Local repository (default): `C:\Users\Admin\.m2\repository`
- Verify:
  ```powershell
  mvn -version
  ```
- Tip: if dependency downloads are slow, configure a mirror repository (for example Alibaba Cloud) in `C:\Users\Admin\.m2\settings.xml`.

### 2.3 MySQL 8.4.9

- Install path: `C:\Program Files\MySQL\MySQL Server 8.4`
- Data directory: `C:\ProgramData\MySQL\MySQL Server 8.4\Data`
- Configuration file: `C:\ProgramData\MySQL\MySQL Server 8.4\my.ini`
- Windows service name: `MySQL84` (startup type Automatic)
- Port: `3306`
- Accounts (all `@localhost`), with separate privileges:

  | Account | Purpose | Privileges on `opsflow` | Credentials |
  |---|---|---|---|
  | `root` | Administration only: create databases and accounts, grant privileges | All | Password chosen by you. Keep it in a password manager. It is **not** stored in an environment variable and must never appear in application configuration. |
  | `opsflow_migrator` | Runs schema migrations (Flyway) | `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES` | `OPSFLOW_MIGRATION_USERNAME` and `OPSFLOW_MIGRATION_PASSWORD` |
  | `opsflow_app` | Runtime access from the application | `SELECT, INSERT, UPDATE` | `OPSFLOW_DB_USERNAME` and `OPSFLOW_DB_PASSWORD` |

  Neither application account has global privileges, `GRANT OPTION`, or access to any other database. `opsflow_app` cannot change the schema. `SELECT ... FOR UPDATE` works with these privileges, which the stock row locking relies on.

  **The `opsflow_app` grants are interim.** Until the tables exist they are database-wide, so `opsflow_app` can still update every table, including the append-only ones. Having no `DELETE` does not make history append-only. They are tightened after the first migration, as described under "Per-table privileges after the first migration" below.
- ⚠️ **Never commit a real password to the repository or hard-code it in configuration files.** The application reads credentials from environment variables (see section 3).
- Character set: `utf8mb4` (set as the server default)
- `mysql.exe` has been added to `PATH`

Common commands:
```powershell
# Start, stop, or restart the service
Start-Service MySQL84
Stop-Service MySQL84
Restart-Service MySQL84

# Connect for administration (type the password at the prompt)
mysql -u root -p
```

Reference `application.yml` configuration for Spring Boot. The application connects as `opsflow_app`, and Flyway connects as `opsflow_migrator`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/opsflow?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=utf8
    username: ${OPSFLOW_DB_USERNAME}
    password: ${OPSFLOW_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    user: ${OPSFLOW_MIGRATION_USERNAME}
    password: ${OPSFLOW_MIGRATION_PASSWORD}
```
None of these variables has a default on purpose: if one is not set, the application fails to start instead of falling back to a value in the repository. The application account is never `root`.

The `opsflow` database and both accounts already exist on this machine. To set them up on another machine, run the following as `root`, replacing the placeholders. Start the client with `mysql -u root -p --histignore="*IDENTIFIED*"` so the typed passwords are not saved in the client's history file:
```sql
CREATE DATABASE IF NOT EXISTS opsflow DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'opsflow_migrator'@'localhost' IDENTIFIED BY '<migrator-password>';
CREATE USER 'opsflow_app'@'localhost' IDENTIFIED BY '<app-password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON opsflow.* TO 'opsflow_migrator'@'localhost';
GRANT SELECT, INSERT, UPDATE ON opsflow.* TO 'opsflow_app'@'localhost';
```
Use long random passwords for the two application accounts, because they are only ever read from environment variables.

Keep the two connections separate in Spring Boot:

| Setting | Account |
|---|---|
| Application database connection (`spring.datasource.*`) | `opsflow_app` |
| Flyway schema creation and upgrades (`spring.flyway.*`) | `opsflow_migrator` |

#### The first migration is applied by Flyway, not by hand

Do not apply `backend/src/main/resources/db/migration/V1__initial_schema.sql` to `opsflow` manually. The first creation of the tables should be done by Flyway when the backend is connected, so that Flyway's schema history table exists from the start. Until then `opsflow` stays empty, and experiments belong in a temporary database.

#### Later migrations

Flyway applies a new migration at the next start of the backend. `V2__seed_roles_and_system_identity.sql` adds the four roles and the SYSTEM identity. It adds rows only, so no grant follows it.

`V3__email_code_request_counts.sql` adds the table `email_code_request_counts`. Privileges were already restricted on this machine before `V3` existed, so after the start that applied `V3` this one line was run as `root` on 2026-11-13. Another machine in the same state needs it too (it is also in the privilege script, for a server that is set up from nothing):
```sql
GRANT INSERT, UPDATE, DELETE ON opsflow.email_code_request_counts TO 'opsflow_app'@'localhost';
```
Until it is run, a request for an email code fails with a permission error (the API answers `INTERNAL_ERROR`).

#### Per-table privileges after the first migration

Privileges can only be granted on tables that exist, so the `opsflow_app` grants above stay database-wide until Flyway has created the tables. Then run [`db/admin/restrict-app-privileges.sql`](db/admin/restrict-app-privileges.sql) as `root`. It replaces them with per-table grants:

| Tables | `opsflow_app` can |
|---|---|
| `inventory_transactions`, `ticket_assignment_history`, `ticket_inventory`, `product_listing_events`, `inventory_item_events`, `order_attempts`, `operation_logs` (append-only) | read and insert |
| `order_items` (frozen after the order is created) | read and insert |
| `users`, `roles`, `products`, `inventory`, `orders`, `tickets` | read, insert, and update |
| `email_verification_codes` | read, insert, update, and delete (expired codes are deleted by a job) |
| `email_code_request_counts` | read, insert, update, and delete (the counts of windows that are over are deleted by the same job) |
| `user_roles` | read and insert |

- Revoking a user's role deletes a `user_roles` row, and deleting a role deletes a `roles` row. When the permission management feature is built, add `GRANT DELETE ON opsflow.user_roles TO 'opsflow_app'@'localhost';` and `GRANT DELETE ON opsflow.roles TO 'opsflow_app'@'localhost';` for those two tables only.
- Every later migration that adds a table must be followed by a grant for that table. A missing grant shows up as a permission error, which is the safe way to fail.
- The script was verified in a temporary database: updates and deletes on the append-only tables are refused, and the updatable tables still work. See [Concurrency and privilege verification](docs/database/database-design.md#concurrency-and-privilege-verification).

> Time zone: OpsFlow stores all times in UTC. The two JDBC parameters above set the session time zone to UTC, so changing only `serverTimezone` is not enough. Run `SET time_zone = '+00:00';` before manual SQL that involves time. See [Time zone](docs/database/database-design.md#time-zone).

### 2.4 Redis 8.10.1

- Install path (executables):
  `C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2`
- Data and configuration directory: `C:\ProgramData\Redis` (`redis.conf` is here)
- Windows service name: `Redis` (registered through **NSSM**, startup type Automatic)
  - NSSM path: `...\NSSM.NSSM_...\nssm-2.24-101-g897c7ad\win64\nssm.exe`
- Port: `6379`, listening on `127.0.0.1`
- No password is set. Local development uses no password by default, and `bind 127.0.0.1` restricts access to this machine.
- `redis-cli` is not on the global `PATH`. To use it, go to the install directory above or add it to `PATH` yourself.

Common commands:
```powershell
Start-Service Redis
Stop-Service Redis
Restart-Service Redis

# Test the connection
& "C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2\redis-cli.exe" -h 127.0.0.1 -p 6379 PING
```

Reference Spring Boot configuration:
```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
```

### 2.5 RabbitMQ 4.3.5

- Install path: `C:\Program Files\RabbitMQ Server\rabbitmq_server-4.3.5`
- Erlang dependency: `C:\Program Files\Erlang OTP` (27.3.4.17, with the machine-level environment variable `ERLANG_HOME` set)
- Windows service name: `RabbitMQ` (startup type Automatic)
- Ports:
  - `5672`: AMQP port (applications connect here)
  - `15672`: web management interface
- Management interface: http://localhost:15672
- Default account: `guest` (**RabbitMQ only lets the guest account log in from localhost by default.** Create a new account for remote access.) Its password is the RabbitMQ product default, which is public knowledge, so it is not repeated here.
- The `rabbitmq_management` plugin was enabled manually.
- The `sbin` directory has been added to `PATH`, so `rabbitmqctl`, `rabbitmq-plugins`, and similar commands work directly.

Common commands:
```powershell
Start-Service RabbitMQ
Stop-Service RabbitMQ
Restart-Service RabbitMQ

# Check status and list queues
rabbitmqctl status
rabbitmqctl list_queues
```

Reference Spring Boot configuration:
```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: /
```

⚠️ **A pitfall during installation**: the Erlang installed first through winget was 29.0.6, which is incompatible with RabbitMQ 4.3.x. RabbitMQ 4.3.x officially supports Erlang 27.x, partly supports 28.x, and does not support 29.x. At startup it failed with `incompatible_feature_flags` and `horus extraction_denied` errors. After uninstalling Erlang 29 and installing Erlang 27.3.4.17, it worked. **Before upgrading Erlang in the future, check the [RabbitMQ compatibility table](https://www.rabbitmq.com/docs/which-erlang).**

### 2.6 Docker Desktop

- Installed through winget (4.90.0). It was first recorded as unusable, because `wsl --install` reported that virtualization was not enabled and `systeminfo` showed "A hypervisor has been detected". The conclusion then was that this machine is itself a virtual machine. **That conclusion was wrong.** The machine is a physical one (an ASUS ROG STRIX Z790-A board with an Intel Core i9-13900KF), and the hypervisor that `systeminfo` reports is Windows' own (Hypervisor enforced Code Integrity).
- On 2026-11-04 the WSL default version was 2, a `docker-desktop` distribution existed, and after starting Docker Desktop, `docker run --rm hello-world` ran correctly (Docker engine 29.7.2).
- Docker Desktop does not start with Windows. Start it before running the integration tests, which use Testcontainers to create a temporary MySQL 8.4 (see [OpsFlow Backend](docs/backend/backend-design.md#tests)).
- The WSL2 virtual machine takes memory. To limit it, create `%UserProfile%\.wslconfig` with a `[wsl2]` section and a `memory=` line.
- MySQL, Redis, and RabbitMQ stay as native Windows services (decided on 2026-11-04). Docker is used only for the temporary MySQL of the tests.

---

## 3. PATH and Environment Variable Summary

All of the following are **user-level** environment variables unless noted. After setting them, **open a new terminal window** for them to take effect:

| Variable | Value |
|---|---|
| `JAVA_HOME` | `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` |
| `M2_HOME` | `C:\Tools\maven` |
| `ERLANG_HOME` (machine level) | `C:\Program Files\Erlang OTP` |

Entries added to `PATH`:
- `C:\Tools\maven\bin`
- `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin`
- `C:\Program Files\MySQL\MySQL Server 8.4\bin`
- `C:\Program Files\RabbitMQ Server\rabbitmq_server-4.3.5\sbin`

### Application secrets

Real credentials live in environment variables and never in the repository. The configuration examples in section 2 read them from these variables:

| Variable | Used for | Required |
|---|---|---|
| `OPSFLOW_DB_USERNAME` | Application account, `opsflow_app` | Yes |
| `OPSFLOW_DB_PASSWORD` | Password of the application account | Yes |
| `OPSFLOW_MIGRATION_USERNAME` | Migration account, `opsflow_migrator` | Yes |
| `OPSFLOW_MIGRATION_PASSWORD` | Password of the migration account | Yes |
| `OPSFLOW_TOKEN_SIGNING_KEY` | The key that signs the login tokens: Base64 of at least 32 random bytes | Yes |
| `OPSFLOW_MAIL_HOST` | The SMTP server that sends the email codes: `smtp.gmail.com` | No. Without it, or without the password, the backend starts and sends no mail |
| `OPSFLOW_MAIL_PORT` | The SMTP port | No, defaults to 587 |
| `OPSFLOW_MAIL_USERNAME` | The address of the Gmail account made for OpsFlow | With the host |
| `OPSFLOW_MAIL_PASSWORD` | The app password of that account, not the password of the Google account | With the host |
| `OPSFLOW_MAIL_FROM` | The sender address, when it differs from the username | No |
| `RABBITMQ_USERNAME` | RabbitMQ user | No, defaults to `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | Yes |

The MySQL `root` password is deliberately not in this list.

On this machine the four database variables, `OPSFLOW_TOKEN_SIGNING_KEY`, `OPSFLOW_MAIL_HOST`, `OPSFLOW_MAIL_USERNAME`, and `OPSFLOW_MAIL_PASSWORD` are set. To set or change a password, type it at a masked prompt so it is not left in the shell history, then open a new terminal and restart the IDE so it picks the value up:
```powershell
$p = Read-Host "App password" -AsSecureString
[Environment]::SetEnvironmentVariable("OPSFLOW_DB_PASSWORD", [System.Net.NetworkCredential]::new("", $p).Password, "User")
```
The token signing key is not typed by a person: it is made at random and stored without being shown. Run this once, then open a new terminal and restart the IDE:
```powershell
$b = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Environment]::SetEnvironmentVariable("OPSFLOW_TOKEN_SIGNING_KEY", [Convert]::ToBase64String($b), "User")
```
Whoever has this key can make a login token for any user, so treat it like a password. Running the lines again makes a new key, and every token issued with the old one stops working, so every user logs in again. The tests do not read this variable: they make their own key for each run.

#### The mail account

OpsFlow sends its email codes through Gmail SMTP, from a Gmail account that was made for OpsFlow and is used for nothing else, so a limit or a block on it never touches a personal mailbox. To let the backend send mail:

1. Sign in to that Google account and turn on 2-Step Verification. Google offers app passwords only with it.
2. Create an app password (Google Account, Security, App passwords). Google shows 16 characters once.
3. Store the three values. The address is not a secret; the app password is, so it is typed at a masked prompt. The spaces that Google shows between the groups are removed:
```powershell
[Environment]::SetEnvironmentVariable("OPSFLOW_MAIL_HOST", "smtp.gmail.com", "User")
[Environment]::SetEnvironmentVariable("OPSFLOW_MAIL_USERNAME", "<gmail-address-of-the-opsflow-account>", "User")
$p = Read-Host "Mail app password" -AsSecureString
[Environment]::SetEnvironmentVariable("OPSFLOW_MAIL_PASSWORD", ([System.Net.NetworkCredential]::new("", $p).Password -replace ' ', ''), "User")
```
4. Open a new terminal and restart the IDE.

An app password can only send and read mail of that account. It can be revoked on the same page without changing the password of the account, and a new one is stored the same way. The tests do not read these variables and send no mail.

To rotate an application password, run `ALTER USER 'opsflow_app'@'localhost' IDENTIFIED BY '<new-password>';` as `root`, then update the variable the same way.

Rules:
- Never write a real password in a document, source file, commit message, or chat. Use a placeholder such as `<app-password>`.
- Run `mysql -u root -p` and type the password at the prompt, instead of putting it on the command line.
- If you prefer a file, use `application-local.yml` or `.env`. Both are ignored by git. Spring Boot does not read `.env` by itself.
- If a secret has ever been committed, treat it as exposed. Change the secret first, then clean the history if needed.

---

## 4. One-Step Self-Check

Open a new terminal and run the following in order:

```powershell
java -version
mvn -version
mysql --version
node -v
npm -v
git --version
rabbitmqctl status
```

Check the service status:
```powershell
Get-Service MySQL84, Redis, RabbitMQ | Select-Object Name, Status, StartType
```

All three services should show `Running` / `Automatic`, which means they start on boot and need no manual start.

---

## 5. Follow-Up Tasks and Suggestions

- [ ] Create the business database manually: `CREATE DATABASE opsflow ...` (see 2.3)
- [ ] Generate the Spring Boot project skeleton with [Spring Initializr](https://start.spring.io) or an IDE (see chapter 3 of the learning documents)
- [ ] When the backend is created, read the MySQL and RabbitMQ credentials from the environment variables in section 3, as in the section 2 examples, and keep `application-local.yml` out of git
- [ ] Frontend: decide between Vue and React (the learning documents mention both), then decide whether a scaffolding CLI is needed (for example `npm create vite@latest`)
