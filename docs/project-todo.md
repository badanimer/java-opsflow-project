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
| T-05 | Add a seed migration for the roles `ADMIN`, `OPERATOR`, and `VIEWER`. An empty `roles` table is expected until then. | When the permission module is implemented | [database-v1.2.md](iterations/database-v1.2.md) |
| T-06 | Grant `DELETE` on `opsflow.user_roles` to the application account, for that table only. | When role revocation is built | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#per-table-privileges-after-the-first-migration) |

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
| T-15 | Refuse role grants to soft-deleted users, and write `ROLE_GRANTED` and `ROLE_REVOKED` rows to `operation_logs` in the same transaction as every change to `user_roles`. | When the permission module is built | [database-v1.3.md](iterations/database-v1.3.md) |
| T-16 | Make every comment correction point at the original comment, not at an earlier correction. The database enforces only that both are on the same ticket. | When the ticket module is built | [Tickets](database/database-design.md#tickets) |
| T-17 | Repeat the concurrency checks against the real application code, including its retry logic, connection pool, and transaction handling. | When T-10 is done | [Concurrency and privilege verification](database/database-design.md#concurrency-and-privilege-verification) |

### Frontend and environment

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |
| T-18 | Choose Vue or React, then decide whether a scaffolding tool such as `npm create vite@latest` is needed. | Before frontend work starts | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#5-follow-up-tasks-and-suggestions) |

## Potential

These are not decided. Each one says what has to be decided.

### Database design

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
| Q-01 | Roles are removed with a physical delete, and `user_roles` keeps no history. | Whether roles need a status instead of physical deletion, and whether grants need a dedicated history table like `ticket_assignment_history`. Either one changes the schema and both ERDs. | [database-v1.3.md](iterations/database-v1.3.md) |
| Q-02 | The grant rule covers soft-deleted users only. | Whether grants to users with status `DISABLED` should also be refused. | [Role grants](database/database-design.md#role-grants) |
| Q-03 | Only role grants and revocations have a defined log format. | A catalog of the other audited actions: their names, targets, and what goes into `before_data` and `after_data`. | [Auditing](database/database-design.md#auditing) |
| Q-04 | The initial model has one inventory record per product and one reservation per order item. | When to add warehouses, a separate reservation table, payment and refund entities, shipment entities, or log partitioning. Each depends on a feature entering the project scope. | [Future Evolution](database/database-design.md#future-evolution) |

### Documents and diagrams

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
| Q-05 | The Business Process Flowchart does not draw the idempotency check. | Whether to add it to the diagram. The check is described in the text of the design document. | [database-v1.2.md](iterations/database-v1.2.md) |
| Q-06 | Git warns that three files under `db/` will change from LF to CRLF the next time Git touches them. | Whether to add a `.gitattributes` file that sets one line-ending rule for the repository. | [db/](../db/) |

### Technology and security

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
| Q-07 | Redis and RabbitMQ are installed but not used. | When to introduce them: Redis for idempotency and rate limiting, RabbitMQ for asynchronous processing. | [README.md](../README.md#implementation-direction) |
| Q-08 | An old database password is still in the Git history, in commit `7096b88`, which is already on the remote. The password was rotated, and the history was deliberately not rewritten. | Whether to rewrite history later, for example before the repository is shared or made public. A rotated password does not need it, but anyone cloning the repository can still read the old value. | Recorded only here; see commit `7096b88` |
| Q-09 | The services run directly on the machine. | Whether to manage MySQL, Redis, and RabbitMQ with Docker Compose, if the machine moves to an environment that supports virtualization. | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#5-follow-up-tasks-and-suggestions) |
