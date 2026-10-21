# OpsFlow Project To-Do

## Purpose

This file is the single list of work that is still to be done on OpsFlow. It has two parts:

- **Confirmed**: tasks that were decided and will be done. Each one has a trigger, which says when it becomes due.
- **Potential**: questions and ideas that are not decided yet. Each one says what has to be decided.

It is written for people planning the next step and for AI tools that need to know what is pending. It lists only open work. Finished work is removed from this file, and its history stays in the iteration files in [`docs/iterations`](iterations/).

The rules for keeping this file current are in [documentation-sync-rules.md](documentation-sync-rules.md).

## How To Use This File

- A task is added here when it is confirmed, or when an iteration file or document records it as an open item.
- A task is removed when it is done. Do not mark it as finished and leave it here.
- A potential item moves to Confirmed when a decision is made, and the decision is recorded in the iteration that makes it. If the answer is "no", the item is removed.
- Every item keeps a link to the document that explains it.

## Confirmed

### Database and migrations

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |
| T-01 | Let Flyway apply `V1__initial_schema.sql` to `opsflow` for the first time. Do not apply it by hand. | When the backend is connected to Flyway | [Initial Migration](database/database-design.md#initial-migration) |
| T-02 | Run `db/admin/restrict-app-privileges.sql` as `root` once. | Right after Flyway applies V1 | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#per-table-privileges-after-the-first-migration) |
| T-03 | Move the migrations to `src/main/resources/db/migration`. Move them, do not copy them. | When the Spring Boot backend is created | [database-v1.2.md](iterations/database-v1.2.md) |
| T-04 | Add a grant for every table that a later migration creates. | With each migration that adds a table | [restrict-app-privileges.sql](../db/admin/restrict-app-privileges.sql) |
| T-05 | Add a seed migration for the roles `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER`. An empty `roles` table is expected until then. | When the permission module is implemented | [database-v1.2.md](iterations/database-v1.2.md) |
| T-06 | Grant `DELETE` on `opsflow.user_roles` and on `opsflow.roles` to the application account, for those two tables only. | When role revocation and role deletion are built | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#per-table-privileges-after-the-first-migration) |

### Backend implementation

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |
| T-07 | Generate the Spring Boot project skeleton. | Next implementation step | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#5-follow-up-tasks-and-suggestions) |
| T-08 | Read the MySQL and RabbitMQ credentials from environment variables, and keep `application-local.yml` out of git. Add the RabbitMQ variables when RabbitMQ is introduced. | When the backend is created | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md) |
| T-09 | Follow the UTC convention: set the JDBC parameters `connectionTimeZone=UTC` and `forceConnectionTimeZoneToSession=true`, and handle Java times in UTC. | When backend code exists | [Time zone](database/database-design.md#time-zone) |
| T-10 | Implement the order and inventory flows as specified: create, confirm, cancel, the timeout job, row locking in ascending inventory id, the status gate, and explicit rollback before every retry. | When the order module is built | [Order and Inventory Flow](database/database-design.md#order-and-inventory-flow) |
| T-11 | Keep the operating parameters in application configuration, not in the database: reservation duration, scan interval, batch size, retry attempts, and retry delays. | With T-10 | [Operating Parameters](database/database-design.md#operating-parameters) |
| T-12 | Generate `ORD-` and `TKT-` numbers in lowercase in application code, and retry with a new UUID on a unique-key collision. | With T-10 and the ticket module | [Order and ticket numbers](database/database-design.md#order-and-ticket-numbers) |
| T-13 | Make API validation limits equal to the database column lengths, so that over-long input is rejected instead of truncated. | When the API is built | [Field Specifications](database/database-design.md#field-specifications) |
| T-14 | Create tickets automatically only for the two agreed cases: inconsistent stock and order records, and exhausted retries that need investigation. Set `type` and `priority` explicitly. | When the ticket module is built | [Exceptions](database/database-design.md#exceptions) |
| T-15 | Refuse role grants to users who are not usable accounts (`status` is not `ACTIVE`, or `deleted` is true), and write `ROLE_GRANTED` and `ROLE_REVOKED` rows to `operation_logs` in the same transaction as every change to `user_roles`. | When the permission module is built | [database-v1.4.md](iterations/database-v1.4.md), [database-v1.3.md](iterations/database-v1.3.md) |
| T-16 | Make every comment correction point at the original comment, not at an earlier correction. The database enforces only that both are on the same ticket. | When the ticket module is built | [Tickets](database/database-design.md#tickets) |
| T-17 | Repeat the concurrency checks against the real application code, including its retry logic, connection pool, and transaction handling. | When T-10 is done | [Concurrency and privilege verification](database/database-design.md#concurrency-and-privilege-verification) |
| T-19 | Count a user's roles in a permission check only when the user is a usable account. Keep the `user_roles` rows when a user is disabled. Set `status` to `DISABLED` when a user is soft-deleted. | When the permission module is built | [Role grants](database/database-design.md#role-grants) |
| T-20 | Make sure a login token issued before an account was disabled stops working, by checking the account on every request or by keeping tokens short-lived. | When authentication is built | [Role grants](database/database-design.md#role-grants) |
| T-21 | Implement role management as specified, for `ADMIN` only: create a role, update its `name` and `description` only, delete it (removing every holder's grant and logging each one in the same transaction, never deleting `ADMIN`), and replace a user's roles in one transaction. Write the role management logs in the same transaction as each change. | When the permission module is built | [Role management](database/database-design.md#role-management) |
| T-22 | Build the pending page that every user passes after login. If `status` is `DISABLED`, show that the account is disabled. Otherwise, if the user has no role, show that the account is waiting for an administrator to assign permissions. Otherwise skip the page. Run the check again on refresh and on the next login. The backend returns the account state, and protected endpoints return an error code that can be told apart from an ordinary denial. | When login and the permission module are built; the frontend part when the frontend starts | [Users without roles](database/database-design.md#users-without-roles) |
| T-23 | Implement Administrator protection: lock the `ADMIN` row in `roles`, count usable `ADMIN` holders after the change, and roll back when the count would be zero. Apply it to disabling or soft-deleting a user, revoking or replacing roles, and deleting a role. Add a concurrency check for two administrators removing each other at the same moment. | With T-21 and the user module | [Administrator protection](database/database-design.md#administrator-protection) |
| T-24 | Create the first administrator at startup, only when no usable `ADMIN` exists, from `OPSFLOW_BOOTSTRAP_ADMIN_USERNAME`, `..._EMAIL`, and `..._PASSWORD`: hash the password, set `must_change_password` to true, grant `ADMIN`, and log `ROLE_GRANTED` with an empty `operator_id`. Fail startup with a clear message when a variable is missing. Run it after the roles are seeded (T-05). | When the user module and the role seed (T-05) exist | [First administrator and forced password change](database/database-design.md#first-administrator-and-forced-password-change) |
| T-25 | Enforce `must_change_password` for the first administrator only: after login, a user with the flag can use nothing except changing the password, and the application clears the flag when the password has been changed. Other accounts set their own password at sign-up. | When authentication is built | [First administrator and forced password change](database/database-design.md#first-administrator-and-forced-password-change) |
| T-26 | Enforce Authority over roles and accounts: `ADMIN` and `MANAGER` are protected roles that nobody can delete or recode; only `ADMIN` creates, updates, or deletes roles and appoints or dismisses managers and administrators; `MANAGER` assigns only roles below manager and only to users who hold neither `ADMIN` nor `MANAGER`, and cannot change its own account. Check the acting and target roles in the same transaction as the change. | With T-15 and T-21 | [Authority over roles and accounts](database/database-design.md#authority-over-roles-and-accounts) |
| T-27 | Review the Permission Matrix, which is a first version, and then implement it: check every endpoint against the acting user's roles, return 403 for a refused action, and hide the controls the user cannot use in the frontend. Keep the permissions of the four seeded roles in application code, keyed by role code. | When the permission module is built | [Permission Matrix](database/database-design.md#permission-matrix) |

### Frontend and environment

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |
| T-18 | Choose Vue or React, then decide whether a scaffolding tool such as `npm create vite@latest` is needed. | Before frontend work starts | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#5-follow-up-tasks-and-suggestions) |

## Potential

These are not decided. Each one says what has to be decided.

### Database design

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
| Q-01 | `user_roles` keeps no history, so the only record of past grants is `operation_logs`. | Whether grants need a dedicated history table like `ticket_assignment_history`. It would change the schema and both ERDs. Roles themselves have no status; that was decided in v1.4. | [database-v1.4.md](iterations/database-v1.4.md) |
| Q-03 | Only role grants, revocations, and role management have a defined log format. | A catalog of the other audited actions: their names, targets, and what goes into `before_data` and `after_data`. | [Auditing](database/database-design.md#auditing) |
| Q-04 | The initial model has one inventory record per product and one reservation per order item. | When to add warehouses, a separate reservation table, payment and refund entities, shipment entities, or log partitioning. Each depends on a feature entering the project scope. | [Future Evolution](database/database-design.md#future-evolution) |

### Documents and diagrams

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |

### Technology and security

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
| Q-07 | Redis and RabbitMQ are installed but not used. | When to introduce them: Redis for idempotency and rate limiting, RabbitMQ for asynchronous processing. | [README.md](../README.md#implementation-direction) |
| Q-09 | The services run directly on the machine. | Whether to manage MySQL, Redis, and RabbitMQ with Docker Compose, if the machine moves to an environment that supports virtualization. | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#5-follow-up-tasks-and-suggestions) |
