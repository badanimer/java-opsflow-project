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

Version 1.2 keeps the table structure and tightens the rules around it. The review found that the tables were sound and that the open risks were in concurrency rules and constraints. This iteration fixes five of the six review points, brings the Business Process Flowchart in line with the reservation flow, records the operating parameters and field specifications, and turns them into the first executable migration, `db/migration/V1__initial_schema.sql`. This covers all six review points.

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
- Verified on MySQL 8.4.9 in a scratch database, since dropped: the script applied cleanly, and 39 checks behaved as expected. These covered accepted valid data, rejected invalid data, composite and per-user keys, `NULL` handling, and restricted deletes. Concurrent behavior (locking, deadlock retry, confirm racing cancel) is not covered; it needs application code.
- Time zone: all times are stored in UTC. The defaults follow the session time zone, so every connection must set it explicitly. The JDBC URL in `ENVIRONMENT_SETUP.md` now uses `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` instead of `serverTimezone=Asia/Shanghai`, and manual SQL runs `SET time_zone = '+00:00';` first. Java must also generate the times it writes in UTC.

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

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated |
| [Initial migration](../../db/migration/V1__initial_schema.sql) | New |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated; shows generic types, the migration holds the exact ones |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Updated; `version` removed |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) | Updated; shows reservation, confirmation, release, timeout, and retry |

## Open Items

- Add a separate seed migration for the roles `ADMIN`, `OPERATOR`, and `VIEWER` when the permission module is implemented. An empty `roles` table is expected until then.
- When the Spring Boot backend is created, move the migrations (not copy them) to `src/main/resources/db/migration`, so only one copy exists.
- Make the application connection and Java time handling follow the UTC convention once backend code exists.
- Validate the design against four scenarios once code exists: confirm and cancel at the same time, repeated submission, a multi-item order where one item is short, and timeout release.
- Decide whether `ticket_comments` may be edited after creation.
