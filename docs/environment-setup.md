# OpsFlow Development Environment

This document describes what a machine needs to develop OpsFlow: the tools, the local MySQL, Redis, and RabbitMQ, and the environment variables the backend reads. It is written for a developer setting up a new machine.

To only run the finished system, none of this is needed except Docker: see [Running It](../README.md#running-it) and [Deployment](backend/backend-design.md#deployment), where every service runs in a container.

The project was developed on Windows 11, so the commands below are PowerShell. The tools themselves run on any operating system.

## Tools

| Tool | Version used | Needed for |
| --- | --- | --- |
| JDK | 17 | The backend. Maven itself comes with the wrapper `backend/mvnw` |
| MySQL | 8.4 | The development database |
| Redis | 8.x | The login counts of each client address. Optional: without it, logins work without the limits of each address |
| RabbitMQ | 4.x, on Erlang 27 | The mail queue. Optional: without it, a mail is sent at once |
| Node.js and npm | Node 21 or later, npm 10 | The frontend. It is pinned to Vite 5 and jsdom 25, which run on Node 21; their newest versions need Node 22 |
| Docker | Docker Desktop on WSL2 | The integration tests (Testcontainers) and the deployment in [`deploy/`](../deploy/) |
| Git | Any recent version | |

Check them in a new terminal:

```powershell
java -version
mysql --version
node -v
npm -v
docker version
```

## MySQL

The development database is `opsflow`, with the character set `utf8mb4`, and three accounts, each with its own rights:

| Account | Purpose | Rights on `opsflow` | Credentials |
| --- | --- | --- | --- |
| `root` | Administration only: create the database and the accounts, grant rights | All | Kept in a password manager. Never in an environment variable or in the configuration of the application |
| `opsflow_migrator` | Runs the Flyway migrations | `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES` | `OPSFLOW_MIGRATION_USERNAME`, `OPSFLOW_MIGRATION_PASSWORD` |
| `opsflow_app` | The application at run time | Per table, see below | `OPSFLOW_DB_USERNAME`, `OPSFLOW_DB_PASSWORD` |

Neither application account has global rights, `GRANT OPTION`, or access to another database. `opsflow_app` cannot change the schema.

Create them as `root`, replacing the placeholders with long random passwords. Start the client with `mysql -u root -p --histignore="*IDENTIFIED*"`, so the passwords are not saved in its history file:

```sql
CREATE DATABASE IF NOT EXISTS opsflow DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'opsflow_migrator'@'localhost' IDENTIFIED BY '<migrator-password>';
CREATE USER 'opsflow_app'@'localhost' IDENTIFIED BY '<app-password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON opsflow.* TO 'opsflow_migrator'@'localhost';
GRANT SELECT, INSERT, UPDATE ON opsflow.* TO 'opsflow_app'@'localhost';
```

The tables are created by Flyway at the first start of the backend, never by hand, so that the history of Flyway exists from the start. A later migration is applied at the next start.

### Per-table privileges after the first migration

Rights can only be granted on tables that exist, so the grant to `opsflow_app` above is database-wide until Flyway has created the tables. After the first start, run [`db/admin/restrict-app-privileges.sql`](../db/admin/restrict-app-privileges.sql) as `root`. It replaces that grant with rights per table:

| Tables | `opsflow_app` can |
| --- | --- |
| `inventory_transactions`, `ticket_assignment_history`, `ticket_inventory`, `product_listing_events`, `inventory_item_events`, `order_attempts`, `operation_logs` (append-only) | read and insert |
| `order_items` (frozen after the order is created) | read and insert |
| `users`, `products`, `inventory`, `orders`, `tickets` | read, insert, and update |
| `roles` | read and update. The roles are fixed; `UPDATE` is there only because MySQL allows `SELECT ... FOR UPDATE` only to an account that may change the table, and the lock on the `ADMIN` row needs it |
| `email_verification_codes`, `email_code_request_counts` | read, insert, update, and delete (a job deletes the expired rows) |
| `user_roles` | read, insert, and delete (taking a role away deletes a row) |

Run the script again after every migration that adds a table: it gives the new table its grant (T-04). Until then the application gets a permission error on that table, which is the safe way to fail. The deployment runs the script on every start by itself. The design behind the rights is in [Concurrency and privilege verification](database/database-design.md#concurrency-and-privilege-verification).

### Time zone

OpsFlow stores every time in UTC. The JDBC URL of the backend sets `connectionTimeZone=UTC` and `forceConnectionTimeZoneToSession=true`, so every session runs in UTC; changing only `serverTimezone` is not enough. Run `SET time_zone = '+00:00';` before manual SQL that involves time. See [Time zone](database/database-design.md#time-zone).

## Redis

A local Redis on `127.0.0.1:6379`, bound to the machine only and without a password, needs no variable. For another server, set `OPSFLOW_REDIS_HOST`, `OPSFLOW_REDIS_PORT`, and `OPSFLOW_REDIS_PASSWORD`. What Redis holds is described in [Login security](database/database-design.md#login-security-and-account-recovery).

## RabbitMQ

A local RabbitMQ on `127.0.0.1:5672` needs no variable: the backend uses the default local account, which RabbitMQ accepts from the same machine only. For a broker on another machine, set `OPSFLOW_RABBITMQ_HOST`, `OPSFLOW_RABBITMQ_PORT`, `OPSFLOW_RABBITMQ_USERNAME`, and `OPSFLOW_RABBITMQ_PASSWORD`. After the first start of the backend, `rabbitmqctl list_queues` shows the mail queue `opsflow.mail` and its dead letters `opsflow.mail.dead` (see [Mail](backend/backend-design.md#mail)).

Check the [RabbitMQ compatibility table](https://www.rabbitmq.com/docs/which-erlang) before installing or upgrading Erlang. RabbitMQ 4.3 does not start on Erlang 29, and fails with `incompatible_feature_flags`; Erlang 27 works.

## Docker Desktop

Docker runs the integration tests, which start MySQL 8.4, Redis, and RabbitMQ in containers through Testcontainers (see [Tests](backend/backend-design.md#tests)), and the deployment in [`deploy/`](../deploy/). Start Docker Desktop before `mvnw test`.

On Windows it runs on WSL2. To limit the memory of the WSL2 virtual machine, create `%UserProfile%\.wslconfig` with a `[wsl2]` section and a `memory=` line.

The deployment has its own MySQL, Redis, and RabbitMQ in containers, with no port on the machine, so it does not touch the development services; only nginx listens, on `127.0.0.1:8000`. Its secrets are in `deploy/.env`, made by `deploy/new-env.ps1` and ignored by git, and are separate from the variables below.

## Environment Variables

Real credentials live in environment variables and never in the repository. The full list, with what each one does, is in [Configuration](backend/backend-design.md#configuration).

| Variable | Required |
| --- | --- |
| `OPSFLOW_DB_USERNAME`, `OPSFLOW_DB_PASSWORD` | Yes |
| `OPSFLOW_MIGRATION_USERNAME`, `OPSFLOW_MIGRATION_PASSWORD` | Yes |
| `OPSFLOW_TOKEN_SIGNING_KEY` | Yes: Base64 of at least 32 random bytes |
| `OPSFLOW_IMAGE_DIR` | No. By default `./data/images` under the folder the backend starts in, which git ignores |
| `OPSFLOW_MAIL_HOST`, `OPSFLOW_MAIL_PORT`, `OPSFLOW_MAIL_USERNAME`, `OPSFLOW_MAIL_PASSWORD`, `OPSFLOW_MAIL_FROM` | No. Without the host or the password the backend starts and sends no mail, so sign-up and password recovery cannot finish |
| `OPSFLOW_REDIS_*`, `OPSFLOW_RABBITMQ_*` | No, see above |

The required ones have no default on purpose: if one is missing, the backend does not start, instead of falling back to a value in the repository. The MySQL `root` password is deliberately not among them.

Type a password at a masked prompt, so it is not left in the shell history, then open a new terminal and restart the IDE:

```powershell
$p = Read-Host "App password" -AsSecureString
[Environment]::SetEnvironmentVariable("OPSFLOW_DB_PASSWORD", [System.Net.NetworkCredential]::new("", $p).Password, "User")
```

The token signing key is made at random and stored without being shown:

```powershell
$b = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Environment]::SetEnvironmentVariable("OPSFLOW_TOKEN_SIGNING_KEY", [Convert]::ToBase64String($b), "User")
```

Whoever has this key can make a login token for any user, so treat it like a password. A new key logs every user out. The tests do not read it: they make their own key for each run.

### The mail account

OpsFlow sends its email codes through Gmail SMTP, from a Gmail account made for OpsFlow and used for nothing else, so a limit or a block on it never touches a personal mailbox.

1. Sign in to that Google account and turn on 2-Step Verification. Google offers app passwords only with it.
2. Create an app password (Google Account, Security, App passwords). Google shows 16 characters once.
3. Store the values. The address is not a secret; the app password is, so it is typed at a masked prompt, without the spaces Google shows between the groups:

```powershell
[Environment]::SetEnvironmentVariable("OPSFLOW_MAIL_HOST", "smtp.gmail.com", "User")
[Environment]::SetEnvironmentVariable("OPSFLOW_MAIL_USERNAME", "<gmail-address-of-the-opsflow-account>", "User")
$p = Read-Host "Mail app password" -AsSecureString
[Environment]::SetEnvironmentVariable("OPSFLOW_MAIL_PASSWORD", ([System.Net.NetworkCredential]::new("", $p).Password -replace ' ', ''), "User")
```

An app password can only send and read mail of that account, and can be revoked without changing the password of the account. The tests send no mail.

### Rules for secrets

- Never write a real password in a document, source file, commit message, or chat. Use a placeholder such as `<app-password>`.
- Run `mysql -u root -p` and type the password at the prompt, instead of putting it on the command line.
- If a file is preferred, use `application-local.yml` or `.env`. Both are ignored by git. Spring Boot does not read `.env` by itself.
- To rotate an application password, run `ALTER USER 'opsflow_app'@'localhost' IDENTIFIED BY '<new-password>';` as `root`, then update the variable.
- A secret that was ever committed is exposed. Change it first, then decide whether to clean the history ([Lessons From The Password Exposure](iterations/database-v1.4.md#lessons-from-the-password-exposure)).

## Running

- The backend: [Running](backend/backend-design.md#running), from `backend/` with the variables set.
- The frontend: [Running the Frontend Locally](frontend/page-design.md#running-the-frontend-locally). The dev server on `http://localhost:5173` passes `/api` to the backend.
- The tests: `mvnw test` from `backend/` with Docker running, and `npm test` from `frontend/`.
