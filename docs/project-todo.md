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
| T-11 | Keep the operating parameters in application configuration, not in the database: reservation duration, scan interval, batch size, retry attempts, retry delays, and the two limits of 99 per person: open tickets created, and tickets in progress. | With T-10 | [Operating Parameters](database/database-design.md#operating-parameters) |
| T-12 | Generate `ORD-` and `TKT-` numbers in lowercase in application code, and retry with a new UUID on a unique-key collision. | With T-10 and the ticket module | [Order and ticket numbers](database/database-design.md#order-and-ticket-numbers) |
| T-13 | Make API validation limits equal to the database column lengths, so that over-long input is rejected instead of truncated. | When the API is built | [Field Specifications](database/database-design.md#field-specifications) |
| T-14 | Create tickets automatically only for the two agreed cases: inconsistent stock and order records, and exhausted retries that need investigation. Set `type` and `priority` explicitly. Let a manual ticket link several inventory records through `ticket_inventory`, ask for at least one on an `INVENTORY_EXCEPTION` ticket, and show the usage rule ("a problem you cannot or may not solve alone") on the ticket form. Refuse to create a ticket, a follow-up included, when the creator already has 99 `OPEN` tickets that they created, counted under an exclusive lock on the creator's user row. | When the ticket module is built | [Exceptions](database/database-design.md#exceptions), [Linked inventory](database/database-design.md#linked-inventory), [database-v1.5.md](iterations/database-v1.5.md) |
| T-15 | Refuse role grants to users who are not usable accounts (`status` is not `ACTIVE`, or `deleted` is true), and write `ROLE_GRANTED` and `ROLE_REVOKED` rows to `operation_logs` in the same transaction as every change to `user_roles`. | When the permission module is built | [database-v1.4.md](iterations/database-v1.4.md), [database-v1.3.md](iterations/database-v1.3.md) |
| T-16 | Make every comment correction point at the original comment, not at an earlier correction. The database enforces only that both are on the same ticket. | When the ticket module is built | [Tickets](database/database-design.md#tickets) |
| T-17 | Repeat the concurrency checks against the real application code, including its retry logic, connection pool, and transaction handling. | When T-10 is done | [Concurrency and privilege verification](database/database-design.md#concurrency-and-privilege-verification) |
| T-19 | Count a user's roles in a permission check only when the user is a usable account. Keep the `user_roles` rows when a user is disabled. Set `status` to `DISABLED` when a user is soft-deleted. | When the permission module is built | [Role grants](database/database-design.md#role-grants) |
| T-20 | Check the account on every request: load the user and its roles from the database, refuse the request when the account is not usable or when the token was issued before `password_changed_at`. The token carries only the user id and its issue time. | When authentication is built | [Login security and account recovery](database/database-design.md#login-security-and-account-recovery) |
| T-21 | Implement role management as specified, for `ADMIN` only: create a role, update its `name` and `description` only, delete it (removing every holder's grant and logging each one in the same transaction, never deleting `ADMIN`), and replace a user's roles in one transaction. Write the role management logs in the same transaction as each change. | When the permission module is built | [Role management](database/database-design.md#role-management) |
| T-22 | Build the pending page that every user passes after login. If `status` is `DISABLED`, show that the account is disabled. Otherwise, if the user has no role, show that the account is waiting for an administrator to assign permissions. Otherwise skip the page. Run the check again on refresh and on the next login. The backend returns the account state, and protected endpoints return an error code that can be told apart from an ordinary denial. | When login and the permission module are built; the frontend part when the frontend starts | [Users without roles](database/database-design.md#users-without-roles) |
| T-23 | Implement Administrator protection: lock the `ADMIN` row in `roles`, count usable `ADMIN` holders after the change, and roll back when the count would be zero. Apply it to disabling or soft-deleting a user, revoking or replacing roles, and deleting a role. Add a concurrency check for two administrators removing each other at the same moment. | With T-21 and the user module | [Administrator protection](database/database-design.md#administrator-protection) |
| T-24 | Create the first administrator at startup, only when no usable `ADMIN` exists, from `OPSFLOW_BOOTSTRAP_ADMIN_USERNAME`, `..._EMAIL`, and `..._PASSWORD`: hash the password, set `must_change_password` to true, grant `ADMIN`, and log `ROLE_GRANTED` with an empty `operator_id`. Fail startup with a clear message when a variable is missing. Run it after the roles are seeded (T-05). | When the user module and the role seed (T-05) exist | [First administrator and forced password change](database/database-design.md#first-administrator-and-forced-password-change) |
| T-25 | Enforce `must_change_password` for the first administrator only: after login, a user with the flag can use nothing except changing the password, and the application clears the flag when the password has been changed. Other accounts set their own password at sign-up. | When authentication is built | [First administrator and forced password change](database/database-design.md#first-administrator-and-forced-password-change) |
| T-26 | Enforce Authority over roles and accounts: `ADMIN` and `MANAGER` are protected roles that nobody can delete or recode; only `ADMIN` creates, updates, or deletes roles and appoints or dismisses managers and administrators; `MANAGER` assigns only roles below manager and only to users who hold neither `ADMIN` nor `MANAGER`, and cannot change its own account. Check the acting and target roles in the same transaction as the change. | With T-15 and T-21 | [Authority over roles and accounts](database/database-design.md#authority-over-roles-and-accounts) |
| T-27 | Review the Permission Matrix, which is a first version, and then implement it: check every endpoint against the acting user's roles, return 403 for a refused action, and hide the controls the user cannot use in the frontend. Keep the permissions of the four seeded roles in application code, keyed by role code. | When the permission module is built | [Permission Matrix](database/database-design.md#permission-matrix) |
| T-28 | Implement the login lockout: count failures per account, lock for 1 minute, 5 minutes, 15 minutes, then until midnight, refuse attempts without checking the password while locked, reset the count at midnight Sydney time (`Australia/Sydney`) and on success or password reset, and never reveal whether a username exists. | When authentication is built | [Login security and account recovery](database/database-design.md#login-security-and-account-recovery) |
| T-29 | Implement sign-up and password recovery with email codes: 6 digits, stored hashed, valid 10 minutes, 5 wrong entries, resend after 60 seconds, only the latest code works. A reset clears the lock and sets `password_changed_at`. | When authentication is built | [Login security and account recovery](database/database-design.md#login-security-and-account-recovery) |
| T-30 | Implement manual stock changes: stock in (`RESTOCK`) and write-off (`WRITE_OFF`) for `OPERATOR`, `MANAGER`, and `ADMIN`; setting the quantity directly (`ADJUST_IN` or `ADJUST_OUT` by the sign of the difference) for `MANAGER` and `ADMIN` only. Require a reason and refuse a change that changes nothing. Lock the `inventory` row, compare a direct set with the value the user saw, and refuse a write-off below zero. In the frontend, operators see only a plus and a minus, and Save is enabled only when the quantity changed and the reason is filled in. Add a concurrency check against order creation on the same product. | With T-10 and T-27; the frontend part when the frontend starts | [Manual stock changes](database/database-design.md#manual-stock-changes), [database-v1.5.md](iterations/database-v1.5.md) |
| T-33 | Implement the ticket status rules in the service layer, because the database checks only the allowed status values: the five transitions and nothing else (never a reopen); an assignee for `IN_PROGRESS`, with removal of the assignee returning the ticket to `OPEN`; a non-blank `resolution`, `resolved_by`, and `resolved_at` for `RESOLVED`; `closed_by` and `closed_at` for `CLOSED`; a non-blank reason, `cancel_reason`, `cancelled_by`, and `cancelled_at` for `CANCELLED`, allowed only from `OPEN` and `IN_PROGRESS`; frozen `CLOSED` and `CANCELLED` tickets (no status, priority, assignee, link, or comment change); an `OPERATOR` moving only their own tickets forward and cancelling only their own (assigned to them, or created by them while `OPEN`); a follow-up only to a `RESOLVED` or `CLOSED` ticket, set at creation and never changed; the archive screen for `CLOSED` tickets, open to every role that can view tickets; batch archive in one transaction per ticket. Cover each rule with a test, so that a missing check fails loudly. | When the ticket module is built | [Status transitions](database/database-design.md#status-transitions), [Follow-up tickets](database/database-design.md#follow-up-tickets), [Archive](database/database-design.md#archive), [database-v1.5.md](iterations/database-v1.5.md) |
| T-35 | Implement taking and reassigning a ticket in the service layer, because the database does not enforce it: the eligible-assignee check (a usable account holding `OPERATOR`, `MANAGER`, or `ADMIN`) on every change of assignee, under a `FOR UPDATE` lock on the new assignee's user row, which also makes the limit safe: refuse when that person already has 99 tickets in progress (assigned to them and `IN_PROGRESS`), when taking, when assigning, when starting (`OPEN` to `IN_PROGRESS`), and when an `IN_PROGRESS` ticket is reassigned to them, because a ticket moved in counts as in progress; taking only an unassigned `OPEN` ticket; reassigning only by a usable `MANAGER` or `ADMIN`, only while the ticket is `OPEN` or `IN_PROGRESS`, only to an eligible assignee who differs from the current one, with a non-blank reason, and only if the assignee the actor saw is still the current one, compared under the ticket row lock; the `ticket_assignment_history` and `operation_logs` rows in the same transaction; the previous assignee losing the right to move or cancel the ticket. Test each rule and the races in Concurrency by Module. | When the ticket module is built | [Who may take a ticket](database/database-design.md#who-may-take-a-ticket), [Reassigning a ticket](database/database-design.md#reassigning-a-ticket), [database-v1.5.md](iterations/database-v1.5.md) |
| T-34 | Implement the rules of Concurrency by Module as each module is built, and repeat the order and inventory style of verification against the real code for tickets, users and roles, and authentication. State the reads, the protection, and the lock order of any new write path in the design document before building it. | With each module; the checks when T-17 is done | [Concurrency by Module](database/database-design.md#concurrency-by-module) |

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
| Q-03 | Only role grants, revocations, and role management have a defined log format. | A catalog of the other audited actions: their names, targets, and what goes into `before_data` and `after_data`. This now includes manual stock changes, and the ticket actions: create, take, assign, move forward, cancel, archive, open a follow-up, and reassign. | [Auditing](database/database-design.md#auditing) |
| Q-04 | The initial model has one inventory record per product and one reservation per order item. | When to add warehouses, a separate reservation table, payment and refund entities, shipment entities, or log partitioning. Each depends on a feature entering the project scope. | [Future Evolution](database/database-design.md#future-evolution) |
| Q-13 | The indexes were designed with the tables, before the list and search screens were known. | Which queries the screens will run, such as orders by status and date, tickets by assignee and status, the archive screen (`CLOSED` tickets, newest first), the follow-up chain of a ticket, logs by operator and time, and whether the existing indexes cover them. Check this before Flyway applies V1, because adding an index later needs a new migration. | [Field Specifications](database/database-design.md#field-specifications) |
| Q-14 | `products` has no category, no unit of measure, and no low-stock threshold. | Whether the customers need any of them. A category or unit adds fields or a table, and a low-stock alert needs a threshold field on `inventory`. | [Product and Inventory](database/database-design.md#product-and-inventory) |
| Q-16 | Nothing says what happens to related data when a user or a product is disabled or soft-deleted. | For a user: what happens to the tickets assigned to them and the orders still in `CREATED`. For a product: whether it can be soft-deleted while stock is reserved or orders still hold it. | [Identity and Access](database/database-design.md#identity-and-access) |
| Q-17 | Operation logs, inventory transactions, and email verification codes only grow, and nothing deletes them. | How long to keep each one, whether to archive or delete old rows, and which privilege the cleanup job would need. | [Future Evolution](database/database-design.md#future-evolution) |
| Q-18 | There is no password rule. | Minimum length and complexity, whether to block common passwords, and whether a new password may equal an old one. | [Login security and account recovery](database/database-design.md#login-security-and-account-recovery) |
| Q-19 | The token expiry is not set. | How long a login token lasts, and whether there is a refresh token or the user logs in again. Every request already checks the account, so a long expiry is less risky than it would be otherwise. | [Login security and account recovery](database/database-design.md#login-security-and-account-recovery) |

### Documents and diagrams

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |

### Technology and security

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
| Q-07 | Redis and RabbitMQ are installed but not used. | When to introduce them: Redis for idempotency and rate limiting, RabbitMQ for asynchronous processing. | [README.md](../README.md#implementation-direction) |
| Q-09 | The services run directly on the machine. | Whether to manage MySQL, Redis, and RabbitMQ with Docker Compose, if the machine moves to an environment that supports virtualization. | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md#5-follow-up-tasks-and-suggestions) |
| Q-12 | Sign-up and password recovery send codes by email, and no email service is set up. | Which service sends the mail (an SMTP provider or a mail API), the sender address, and how its credentials are kept in environment variables. | [Login security and account recovery](database/database-design.md#login-security-and-account-recovery) |
