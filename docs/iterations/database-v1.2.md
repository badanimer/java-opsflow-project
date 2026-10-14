# Database Iteration v1.2

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.2 |
| Date | 2026-10-14 |
| Baseline | v1.1, see [database-v1.1.md](database-v1.1.md) |
| Type | Design iteration (ERD, documentation, and the first migration) |
| Table count | 12 (unchanged) |
| Trigger | An external design review of the v1.1 physical ERD and design document |

## Summary

Version 1.2 keeps the table structure and tightens the rules around it. The review found that the tables were sound and that the open risks were in concurrency rules and constraints. This iteration fixes five of the six review points, brings the Business Process Flowchart in line with the reservation flow, records the operating parameters and field specifications, and turns them into the first executable migration, `db/migration/V1__initial_schema.sql`. This covers all six review points. Follow-up decisions then fixed the comparison rules of the generated identifiers, made ticket comments append-only, turned the per-table privileges into a script, and verified the schema, the privileges, and the concurrency rules against a real MySQL server.

## Changes

### Concurrency strategy

- Stock changes now use pessimistic row locking as the single strategy. Every flow locks the affected `inventory` rows with `SELECT ... FOR UPDATE` in ascending inventory id, validates, updates, and writes the inventory transaction in one transaction.
- Inventory transaction snapshots are read from the locked row, so concurrent requests can no longer record the same stale value.
- `inventory.version` was removed from the physical and simplified ERDs. It had been labelled an optimistic lock, but no rule checked it.
- The lock order is fixed (`orders` row, then `inventory` rows by ascending id). Deadlock and lock-wait errors roll back the whole transaction and retry a limited number of times.

### Confirm order

- A confirmation that updates zero rows is no longer treated as "already processed". The flow now reads the order and distinguishes four cases: not found, final status, `CREATED` but expired, and `CREATED` but not expired.
- An expired confirmation runs the cancel path immediately and commits the release, instead of waiting for the timeout job.
- `reserved_until` is compared with the database clock.

### Order items are frozen

- After an order is created, its items and amounts must not be edited or deleted, because confirm and release read `order_items.quantity`. Changing an order means cancelling it and creating a new one.
- `order_items.updated_at` was removed from the physical ERD.

### Constraints made explicit

- Composite unique keys are written in the key column of the physical ERD, for example `UK(user_id, idempotency_key)`, so they are not created as a unique key on one column.
- `inventory_transactions.order_id` is required for `RESERVE`, `CONFIRM`, and `RELEASE`, and must be empty for the other types. `quantity` must be greater than zero, and the snapshots must not be negative.
- Foreign keys from history tables must not cascade deletes.

### Operating parameters and field specifications

- New Operating Parameters section: 30-minute reservation, 1-minute timeout scan of up to 100 orders, and up to 3 attempts on deadlock or lock-wait timeout with 50 ms and 150 ms waits plus random delay. These live in application configuration. The database stores only each order's `reserved_until`.
- New Field Specifications section: types, lengths, and precision for money (`DECIMAL(12,2)`, `DECIMAL(14,2)`), quantities, keys, times (`DATETIME(3)` in UTC), and every string field. `roles.name` and `roles.description` were not covered by the supplied values and default to `VARCHAR(64)` and `VARCHAR(255)`.
- Automatic ticket creation is limited to stock and order inconsistency, and to exhausted retries that need investigation. Insufficient stock, duplicate requests, normal cancellation, and normal timeout create no ticket.

### Initial migration

- New [`db/migration/V1__initial_schema.sql`](../../db/migration/V1__initial_schema.sql) creates all 12 tables for MySQL 8.0.16 or later, using the Field Specifications. Every foreign key uses `ON DELETE RESTRICT` so history is never cascaded or nulled out.
- Indexes: `orders(status, reserved_until)` for the timeout job, `inventory_transactions(inventory_id, created_at)`, `tickets(assignee_id, status)`, `operation_logs(target_type, target_id)`, and an index on every foreign key.
- Extra `CHECK` constraints beyond the design rules: an order is `CREATED` exactly when `finished_at` is empty, an order item's `subtotal` equals `unit_price * quantity`, inventory transaction snapshots agree with the type and quantity, and a ticket assignment must change the assignee.
- `tickets.type` and `tickets.priority` are required with no default, so callers must choose them, including code that creates tickets automatically. A form may preselect `MEDIUM`, but the endpoint must receive the priority explicitly. `tickets.status` defaults to `OPEN`.
- Verified on MySQL 8.4.9 in a temporary database. See Concurrency and privilege verification below.
- Time zone: all times are stored in UTC. The defaults follow the session time zone, so every connection must set it explicitly. The JDBC URL in `ENVIRONMENT_SETUP.md` now uses `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` instead of `serverTimezone=Asia/Shanghai`, and manual SQL runs `SET time_zone = '+00:00';` first. Java must also generate the times it writes in UTC.

### Identifiers

- `orders.order_no`, `tickets.ticket_no`, and `orders.idempotency_key` use the `utf8mb4_0900_bin` collation, so they are compared exactly. Before, the default collation treated `Abc` and `abc` as the same key. Usernames, emails, SKUs, and role codes keep the default case-insensitive rule.
- `order_no` is `ORD-` plus a random UUID without hyphens, and `ticket_no` is `TKT-` plus the same, generated in lowercase by the application. The unique constraint stays as the final guard, and a collision is retried with a new UUID.

### Ticket comments are append-only

- Comments are never edited or deleted, and a mistake is corrected by adding a new comment. `ticket_comments.updated_at` was removed from the migration and the physical ERD.
- The application account therefore gets only `SELECT` and `INSERT` on `ticket_comments`. This replaces the earlier plan to keep the table updatable.
- A nullable `corrects_comment_id` links a correction to the comment it corrects. It is backed by the composite foreign key (`ticket_id`, `corrects_comment_id`) to (`ticket_id`, `id`) and a unique key on (`ticket_id`, `id`), so the database itself rejects a correction that points at another ticket's comment. Every correction points at the original comment. V1 had not been applied, so the field was added to V1 directly.

### Per-table privileges

- Having no `DELETE` does not make history append-only while `UPDATE` is granted database-wide. The correction: `db/admin/restrict-app-privileges.sql` replaces the database-wide grants with per-table ones. `inventory_transactions`, `ticket_assignment_history`, `ticket_comments`, `operation_logs`, and `order_items` become insert-only, and `user_roles` is insert-only until the permission feature needs `DELETE` on that table alone.
- Run it as `root` after Flyway applies the first migration. It is not a migration, because the migration account has no `GRANT` privilege and privileges belong to the server.

### First application of the migration

- V1 is not applied to the real `opsflow` database by hand. Flyway applies it when the backend is connected, so its schema history exists from the start. Experiments use a temporary database.

### Concurrency and privilege verification

- Script: `db/verification/run-db-verification.ps1`, run against MySQL 8.4.9 in a temporary database with temporary accounts, under the restricted application account. The real `opsflow` database was not touched.
- Result: 62 checks passed, 0 failed in the default run (4 to 12 rounds per race). An earlier 24-round stress run of the same races also passed, before the comment-correction checks were added.
- Confirmed: confirm versus cancel, repeated submission, partial-stock rollback, no oversell with an unbroken snapshot chain, double timeout jobs, expired confirmation, deadlock handling, lock wait timeout behavior, case-sensitive identifiers, correction comments (same-ticket rule, repeated corrections, missing target, delete protection), and per-table privileges (append-only and frozen tables refuse UPDATE and DELETE).
- Negative control: reading stock without a lock breaks the snapshot chain, so the check detects what row locking prevents.
- Limits: the procedures are an executable specification, not the application code, and the run uses one server at `REPEATABLE READ`. See [Concurrency and privilege verification](../database/database-design.md#concurrency-and-privilege-verification).

### Business Process Flowchart

- The creation flow now creates the order, locks stock rows, checks availability, reserves stock, and records `RESERVE` transactions.
- New "Retry allowed? (max 3)" decision in exception handling, returning to order creation in a new transaction.
- New sections "Order Confirmation" and "Order Cancellation and Timeout" cover the status gate, the confirmation outcomes including the expired branch, and release.
- The idempotency check is not drawn.

### Document alignment

- The note about unsynchronized diagrams now states that the Simplified ERD reflects reservation, and that the Business Process Flowchart did not until this iteration.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Primary concurrency strategy | Pessimistic row locking, no `version` field | Optimistic locking with a version check and retry |
| Changing a created order | Not allowed; cancel and recreate | Allowing edits and adjusting the reservation |
| Frozen order items | Remove `order_items.updated_at` | Keep the column and rely on the rule alone |
| Expired confirmation | Cancel and release at once | Return the order unchanged and wait for the timeout job |
| Operating parameters and field types | Adopt the values recommended during the review | Leave them to the migration author |
| Migration location | `db/migration/` at the repository root, named `V1__initial_schema.sql` | Inside a backend module, which does not exist yet |
| Integrity checks | Add `CHECK` constraints for order finish state, item subtotal, inventory movement, and assignment change | Enforce these only in the service layer |
| Identifier comparison | `utf8mb4_0900_bin` for `order_no`, `ticket_no`, and `idempotency_key` only | Binary comparison for every unique column, or keep the default |
| Order and ticket numbers | `ORD-` or `TKT-` plus a random UUID without hyphens, with the unique constraint kept | A sequence, or a date-based format |
| First application of V1 | Flyway, when the backend exists | Applying it by hand now and baselining Flyway later |
| Ticket comments | Append-only, corrected by a new comment that links to the original through `corrects_comment_id` | Editable comments, or a correction described only in the comment text |
| Same-ticket rule for corrections | Composite foreign key (`ticket_id`, `corrects_comment_id`), enforced by the database | A check in the service layer only |
| Application privileges | Per-table, after the first migration, history tables insert-only | Database-wide `INSERT` and `UPDATE` with service-layer rules only |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated |
| [Initial migration](../../db/migration/V1__initial_schema.sql) | New |
| [Per-table privilege script](../../db/admin/restrict-app-privileges.sql) | New; run once after the first migration |
| [Verification scripts](../../db/verification/) | New; the concurrency fixtures and the runner |
| [Environment setup](../../ENVIRONMENT_SETUP.md) | Updated; accounts, UTC, environment variables |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated; shows generic types, the migration holds the exact ones |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Updated; `version` removed |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) | Updated; shows reservation, confirmation, release, timeout, and retry |

## Open Items

- When Flyway first applies V1, run `db/admin/restrict-app-privileges.sql` as `root`. Add `GRANT DELETE ON opsflow.user_roles` when the permission feature is built.
- Add a separate seed migration for the roles `ADMIN`, `OPERATOR`, and `VIEWER` when the permission module is implemented. An empty `roles` table is expected until then.
- When the Spring Boot backend is created, move the migrations (not copy them) to `src/main/resources/db/migration`, so only one copy exists.
- Make the application connection and Java time handling follow the UTC convention once backend code exists.
- Generate `ORD-` and `TKT-` numbers in application code, and retry with a new UUID on a unique-key collision.
- Repeat the concurrency checks against the real application code, including its retry logic, connection pool, and transaction handling. The verification so far runs the flows as stored procedures.
- Re-run `db/verification/run-db-verification.ps1` whenever the schema, the privileges, or the flows change.
