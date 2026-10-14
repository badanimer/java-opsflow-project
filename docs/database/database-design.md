# OpsFlow Database Structure and Business Flow

## Purpose

This document defines the initial database boundaries, entity relationships, and core business flow for OpsFlow. It serves as the design baseline for implementation. It does not replace executable database migration scripts.

The initial model contains 12 tables across five domains: identity and access, product and inventory, orders, tickets, and auditing.

## Change Log

This document always describes the current design. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-10-14 | 1.2 | Unified stock concurrency on pessimistic row locking, clarified expired confirmation, froze order items, made composite keys explicit, and added the initial migration | [database-v1.2.md](../iterations/database-v1.2.md) |
| 2026-10-14 | 1.1 | Added stock reservation, redefined order statuses, and aligned the document with the physical ERD | [database-v1.1.md](../iterations/database-v1.1.md) |
| Before 2026-10-14 | 1.0 | Initial database design baseline with 12 tables | Commit `7ab5f55` |

## Design Diagrams

| Diagram | Purpose | Link |
| --- | --- | --- |
| Simplified ERD | Provides a concise overview of the core entities and relationships | [Open Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) |
| Physical ERD | Defines implementation-oriented fields, keys, and relationships | [Open Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) |
| Business Process Flowchart | Describes the order, inventory, audit, and exception-ticket flow | [Open Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) |

The Simplified ERD reflects inventory reservation. The Business Process Flowchart still shows the earlier flow, with direct stock deduction and no confirm, cancel, release, or timeout steps. Update it to match the flow in this document.

## Table Overview

### Identity and Access

| Table | Responsibility |
| --- | --- |
| `users` | Stores user accounts, identity information, and account status |
| `roles` | Defines system roles |
| `user_roles` | Associates users with roles and supports multiple roles per user |

`users` and `roles` have a many-to-many relationship implemented through `user_roles`. A user cannot be granted the same role twice, so `user_roles` has a composite unique key on `(user_id, role_id)`.

`users` and `products` use soft deletion (`status` plus `deleted`). Unique values such as `username`, `email`, and `sku` stay reserved after soft deletion and cannot be reused. This protects historical orders and audit records.

### Product and Inventory

| Table | Responsibility |
| --- | --- |
| `products` | Stores product details, prices, and sales status |
| `inventory` | Stores the current stock position and concurrency-control data for each product |
| `inventory_transactions` | Records every stock movement: restocks, adjustments, reservations, confirmations, and releases |

In the initial version, each product has one current inventory record, so `products` and `inventory` have a one-to-one relationship. An inventory record has many inventory transactions. A transaction references its inventory record only; the product is reached through `inventory`.

`inventory` represents the current state for efficient reads. `inventory_transactions` preserves the history required to explain why stock changed.

#### Stock quantities

| Field | Meaning |
| --- | --- |
| `available_quantity` | Stock that can be sold now. It excludes reserved stock. |
| `locked_quantity` | Stock reserved by orders in `CREATED` status |
| `sold_quantity` | Stock permanently deducted by completed orders |

All three quantities must stay greater than or equal to zero. Stock still held in the warehouse is `available_quantity + locked_quantity`. Stock is changed only under pessimistic row locking; see Concurrency and duplicate protection.

#### Inventory transaction types

| Type | Effect on `inventory` |
| --- | --- |
| `RESTOCK` | `available_quantity` increases |
| `ADJUST_IN` | `available_quantity` increases (manual correction) |
| `ADJUST_OUT` | `available_quantity` decreases (manual correction) |
| `RESERVE` | `available_quantity` decreases, `locked_quantity` increases |
| `CONFIRM` | `locked_quantity` decreases, `sold_quantity` increases |
| `RELEASE` | `locked_quantity` decreases, `available_quantity` increases |

`quantity` is always greater than zero; the type determines the direction. Each transaction stores `available_before`, `available_after`, `locked_before`, and `locked_after` so that any movement can be audited without replaying history. The snapshots are taken from the inventory row while it is locked by the same transaction, so they are exact.

`order_id` is required for `RESERVE`, `CONFIRM`, and `RELEASE`, and must be empty for `RESTOCK`, `ADJUST_IN`, and `ADJUST_OUT`. `operator_id` is empty for system actions such as timeout release.

### Orders

| Table | Responsibility |
| --- | --- |
| `orders` | Stores order-level information such as the customer, order number, total amount, status, and reservation deadline |
| `order_items` | Stores the purchased product snapshot, quantity, and transaction price |

A user can create many orders. Each order must contain one or more order items. Each `order_items` record references exactly one product, and an order contains at most one item per product (`UK(order_id, product_id)`).

- `users` 1:N `orders`
- `orders` 1:N `order_items`
- `products` 1:N `order_items`

An order item should retain the product name or SKU and price as they were when the order was created. Later product updates must not change the meaning of historical orders.

Once an order is created, its items and amounts are frozen. The product, quantity, price, and composition of the items, as well as `total_amount` and `item_count`, must not be edited or deleted. Confirm and release read `order_items.quantity` to decide how much stock to move, so a changed quantity would move the wrong amount. To change an order, cancel it and create a new one. For the same reason `order_items` has `created_at` but no `updated_at`.

#### Order status

| Status | Meaning |
| --- | --- |
| `CREATED` | The order exists and its stock is reserved until `reserved_until` |
| `COMPLETED` | The order was confirmed and the reservation became a permanent deduction |
| `CANCELLED` | The order was cancelled or its reservation timed out, and the reservation was released |
| `FAILED` | The order ended with a definite business failure, and the reservation was released |

`CREATED` is the only non-final status. The only valid transitions are `CREATED → COMPLETED`, `CREATED → CANCELLED`, and `CREATED → FAILED`. There is no payment status because payment is outside the current system boundary.

Additional `orders` fields support this lifecycle:

- `reserved_until` is the deadline for the stock reservation.
- `finished_at` is set when the order reaches a final status.
- `status_reason` records why an order was cancelled or failed.
- `idempotency_key` is optional and unique per user (`UK(user_id, idempotency_key)`), so a repeated submission returns the original order.

### Tickets

| Table | Responsibility |
| --- | --- |
| `tickets` | Stores exception tickets, the fixed creator, current assignee, resolver, status, and priority |
| `ticket_assignment_history` | Records every assignment and reassignment, including the previous and new assignees |
| `ticket_comments` | Stores investigation notes, communication, and resolution details |

The ticket creator is fixed in `created_by`. `assignee_id` contains only the current assignee, and a ticket has at most one current assignee at a time. Each assignee change creates a new `ticket_assignment_history` record instead of overwriting the assignment history.

A ticket may initially be unassigned and may be reassigned multiple times during its lifecycle. Each reassignment should record the previous assignee (`from_user_id`), new assignee (`to_user_id`), acting user (`changed_by`), reason, and timestamp. `from_user_id` is empty for the first assignment, and `to_user_id` is empty when a ticket is unassigned.

`tickets.type` uses `ORDER_EXCEPTION`, `INVENTORY_EXCEPTION`, or `OTHER`. `tickets.status` uses `OPEN`, `IN_PROGRESS`, `RESOLVED`, or `CLOSED`. `tickets.priority` uses `LOW`, `MEDIUM`, `HIGH`, or `URGENT`. `order_id` is optional because not every ticket relates to an order.

`type` and `priority` are required and have no database default, so a missing value fails instead of silently producing a wrong one. `status` defaults to `OPEN`. A ticket form may preselect `MEDIUM`, but the submit endpoint must still receive the priority explicitly, and the program must set both type and priority when it creates a ticket automatically.

### Auditing

| Table | Responsibility |
| --- | --- |
| `operation_logs` | Records important business actions, actors, target objects, and relevant state changes |

The initial version uses a generic target reference. `target_type` and `target_id` identify an order, inventory record, ticket, or another business object. The application maintains this polymorphic relationship because it cannot be represented by one conventional foreign-key constraint. `before_data` and `after_data` store the changed state as JSON. `operator_id` is empty for system actions.

## Relationship Summary

| Source | Cardinality | Target | Description |
| --- | --- | --- | --- |
| `users` | M:N | `roles` | Implemented through `user_roles` |
| `users` | 1:N | `orders` | A user creates multiple orders |
| `orders` | 1:N | `order_items` | An order contains at least one item |
| `products` | 1:N | `order_items` | Each item references one product |
| `products` | 1:1 | `inventory` | Each product has one current inventory record in the initial version |
| `inventory` | 1:N | `inventory_transactions` | An inventory record produces multiple transactions |
| `orders` | 0..1:N | `inventory_transactions` | Reserve, confirm, and release transactions reference their order |
| `users` | 0..1:N | `inventory_transactions` | A user may perform manual or confirming operations; system actions have no user |
| `orders` | 0..1:N | `tickets` | An order can produce multiple tickets for separate exceptions |
| `users` | 1:N | `tickets` | A user creates tickets |
| `users` | 0..1:N | `tickets` | A user is the current assignee of, or resolves, multiple tickets |
| `tickets` | 1:N | `ticket_assignment_history` | A ticket retains its complete assignment history |
| `users` | 1:N | `ticket_assignment_history` | A user appears as the previous assignee, new assignee, or acting user |
| `tickets` | 1:N | `ticket_comments` | A ticket contains processing notes and communication |
| `users` | 0..1:N | `operation_logs` | A user performs multiple audited operations; system actions have no user |

## Order and Inventory Flow

### Create order

1. An authenticated user submits an order containing one or more products and optionally an idempotency key.
2. If the key already exists for this user, the system returns the original order without changing anything. If two identical submissions race, the second insert violates `UK(user_id, idempotency_key)`; it rolls back and returns the existing order.
3. The system validates that each product exists, is available for sale, and has a valid requested quantity.
4. Within one database transaction, the system:
   1. creates `orders` with status `CREATED` and a `reserved_until` deadline;
   2. creates the related `order_items`;
   3. locks the `inventory` row of every item with `SELECT ... FOR UPDATE`, in ascending inventory id, and reads the current quantities;
   4. checks that `available_quantity >= q` for every item. If any item is short, the whole transaction rolls back and the caller receives an out-of-stock result. No order is created and no stock changes, even when other items in the same order had enough stock;
   5. for each item, decreases `available_quantity` and increases `locked_quantity` by `q`, and inserts a `RESERVE` row into `inventory_transactions` whose snapshots come from the values read in step 3;
   6. writes the required `operation_logs`.
5. The transaction commits only when every step succeeds.

### Confirm order

An operator or a confirmation endpoint completes the order.

1. In one transaction, run the status gate: update the order to `COMPLETED` and set `finished_at` with `WHERE id = ? AND status = 'CREATED' AND reserved_until > NOW()`.
2. If one row was updated, the order is confirmed. Lock the `inventory` rows of its items in ascending inventory id, check `locked_quantity >= q`, decrease `locked_quantity` and increase `sold_quantity` by `q`, and insert a `CONFIRM` transaction for each item. Write `operation_logs` and commit.
3. If zero rows were updated, read the order and decide by what it shows. Zero rows does not by itself mean another request got there first.

| Order state | Meaning | Action |
| --- | --- | --- |
| Does not exist | Invalid request | Return not found |
| Final status (`COMPLETED`, `CANCELLED`, `FAILED`) | Already processed | Return the current result. Do not touch inventory. |
| `CREATED` and `reserved_until` has passed | The reservation expired | Run the cancel path with status `CANCELLED` and reason `RESERVATION_EXPIRED`, commit it, and return an "expired and cancelled" result |
| `CREATED` and `reserved_until` has not passed | Another transaction changed the order between the gate and the read | Retry from step 1 |

An expired confirmation releases the stock immediately instead of waiting for the scheduled job. That cancellation is committed, not rolled back.

### Cancel, time out, or fail an order

Cancellation by a user or operator, timeout, and definite business failure share one path.

1. In one transaction, run the status gate: update the order with `WHERE id = ? AND status = 'CREATED'`, setting the target status (`CANCELLED` or `FAILED`), `finished_at`, and `status_reason`.
2. If zero rows were updated, the order is already final. Stop without touching inventory and return the current result.
3. Otherwise, lock the `inventory` rows of the order's items in ascending inventory id, check `locked_quantity >= q`, decrease `locked_quantity` and increase `available_quantity` by `q`, and insert a `RELEASE` transaction for each item.
4. Write `operation_logs` and commit.

A scheduled job runs every minute, finds up to 100 orders with `status = 'CREATED'` and `reserved_until` in the past, and cancels each one in its own transaction. Concurrent job runs are safe because only the transaction that wins the status update releases stock.

Transient database or interface errors must roll back the transaction and leave the order `CREATED` so the operation can be retried. They must never set `FAILED`. `FAILED` is reserved for a definite business outcome.

### Concurrency and duplicate protection

- **Strategy: pessimistic row locking.** Every stock change locks the affected `inventory` rows with `SELECT ... FOR UPDATE`, reads the quantities, validates, updates, and writes the `inventory_transactions` rows, all in one transaction. A plain `SELECT` must not be used to read quantities for a later update, because another transaction can change them in between and the snapshots would be wrong. There is no version counter on `inventory`, and optimistic locking is not used, so the two strategies are never mixed.
- **Lock order.** Every flow locks the `orders` row first (by inserting it or by running the status gate), then the `inventory` rows in ascending inventory id. A fixed order lowers the chance of deadlock but does not remove it.
- **Deadlock and lock-wait retry.** If the database reports a deadlock or a lock wait timeout, the transaction is rolled back completely and retried in a new transaction, up to the attempt limit in Operating Parameters. MySQL rolls back the whole transaction on a deadlock, but a lock wait timeout may roll back only the failed statement, so the application must explicitly roll back before it retries. Retries are safe because the status gate and the unique constraints below prevent a repeated operation from taking effect twice.
- **Status gate.** The conditional status update is the single gate for every transition. Because only one transaction can move an order out of `CREATED`, confirm and cancel can never both succeed, and repeated requests cannot deduct or release stock twice.
- **Constraint backstop.** `CHECK` constraints keep stock quantities non-negative, so a logic error fails instead of corrupting stock.
- **Duplicate inventory transactions.** `UK(order_id, inventory_id, type)` on `inventory_transactions` is a second line of defense. A duplicate `RESERVE`, `CONFIRM`, or `RELEASE` for the same order and inventory record violates the constraint and rolls back. Stock-level types have no order, and the constraint does not apply to them, so repeated restocks are allowed.
- **Duplicate orders.** `UK(user_id, idempotency_key)` prevents duplicate order creation from repeated submissions. It is a composite key, not a unique key on `idempotency_key` alone.
- **Time source.** Compare `reserved_until` with the database clock, not with application server clocks.

### Exceptions

Situations that need manual investigation create `tickets`, which then move through assignment, investigation, reassignment, and resolution. In the first version, only cases that need a person create a ticket automatically:

| Creates a ticket | Does not create a ticket |
| --- | --- |
| Stock and order records are inconsistent | Insufficient stock |
| Retries are exhausted and the cause needs investigation | Duplicate or repeated requests |
| | Normal cancellation |
| | Normal reservation timeout |

## Operating Parameters

These values belong in application configuration, not in the database. The database stores only the actual `reserved_until` of each order, so changing the configured duration does not affect existing orders.

| Parameter | Value | Reason |
| --- | --- | --- |
| Reservation duration | 30 minutes | Operators confirm orders and need time to process them |
| Timeout scan interval | 1 minute | Simple, and expired stock is usually released within about a minute |
| Orders per scan batch | 100 | Avoids processing too many at once; each order uses its own transaction |
| Attempts on deadlock or lock-wait timeout | 3 in total, including the first | At most two retries, so retrying never loops |
| Wait between retries | 50 ms, then 150 ms, each with a small random delay | Prevents conflicting requests from retrying at the same moment |

Retry only after the previous transaction has been fully rolled back, and run the retry in a new transaction.

## Field Specifications

The physical ERD shows generic types. The migration must use the types below. Keep API validation limits equal to the database lengths so that over-long input is rejected instead of being silently truncated.

### Numbers, money, and time

| Field | Type and rule |
| --- | --- |
| Product price, order item unit price | `DECIMAL(12,2)`, not negative |
| Order item subtotal, order total amount | `DECIMAL(14,2)`, not negative |
| Stock quantities, order quantity, transaction quantity | `INT`. Stock quantities are not negative. Order and transaction quantities are greater than zero. |
| Primary keys and foreign keys | `BIGINT`, with the same type on both sides of every relationship |
| Creation, update, expiry, and completion times | `DATETIME(3)`, stored in UTC |

Money is kept to two decimal places and calculated with Java `BigDecimal`.

### Strings

| Field | Type |
| --- | --- |
| `users.username` | `VARCHAR(64)` |
| `users.email` | `VARCHAR(254)` |
| `users.password_hash` | `VARCHAR(255)` |
| `products.sku`, `order_items.product_sku` | `VARCHAR(64)` |
| `products.name`, `order_items.product_name` | `VARCHAR(200)` |
| `orders.order_no`, `tickets.ticket_no` | `VARCHAR(64)` |
| `orders.idempotency_key` | `VARCHAR(128)` |
| Status, type, and priority fields, `roles.code` | `VARCHAR(32)`, restricted to the allowed values |
| `tickets.title` | `VARCHAR(200)` |
| `orders.status_reason`, `inventory_transactions.reason`, `ticket_assignment_history.reason` | `VARCHAR(255)` |
| `remark` fields | `VARCHAR(500)` |
| `operation_logs.ip_address` | `VARCHAR(45)` |
| `operation_logs.action`, `operation_logs.target_type` | `VARCHAR(64)` |
| Product and ticket descriptions, `tickets.resolution`, `ticket_comments.content` | `TEXT` |
| `operation_logs.before_data`, `after_data` | `JSON` |

`roles.name` and `roles.description` were not covered by the supplied values. Use `VARCHAR(64)` and `VARCHAR(255)` unless a reason to change appears.

### Time zone

Times are stored in UTC. `DATETIME` carries no time zone, and the defaults for `created_at` and `updated_at` use `CURRENT_TIMESTAMP(3)`, which follows the session time zone. Application code and the database must therefore follow one convention:

- Set the session time zone of every application connection to UTC. Changing only `serverTimezone` is not enough. For Connector/J, use `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`.
- Have Java generate every time it writes, such as `reserved_until`, in UTC.
- Run `SET time_zone = '+00:00';` before any manual SQL that involves time.
- Convert to a local time zone only when displaying times in the user interface.

## Initial Migration

The first executable migration is [`db/migration/V1__initial_schema.sql`](../../db/migration/V1__initial_schema.sql). It creates all 12 tables for MySQL 8.0.16 or later, using the types in Field Specifications. It creates structure only. The roles `ADMIN`, `OPERATOR`, and `VIEWER` are added by a separate seed migration when the permission module is implemented, so an empty `roles` table is expected for now. Never edit a migration that has been applied. Add a new one.

Run migrations with the `opsflow_migrator` account, which can change the schema. The application connects as `opsflow_app`, which can only read, insert, and update rows in `opsflow`. See [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md#23-mysql-849) for the accounts and their credentials.

When the Spring Boot backend is created, move the migrations to its `src/main/resources/db/migration/`, which Flyway reads by default (`classpath:db/migration`). Move them, do not copy them, so that only one copy exists.

Beyond the rules listed in Initial Constraints, the migration adds these checks, each verified against MySQL 8.4:

- An order is `CREATED` exactly when `finished_at` is empty.
- An order item's `subtotal` equals `unit_price * quantity`.
- The snapshots of an inventory transaction agree with its type and quantity. For example, `RESERVE` lowers `available` and raises `locked` by `quantity`.
- A ticket assignment record must change the assignee. A move from empty to empty, or from a user to the same user, is rejected.

## Initial Constraints

- Use numeric `id` values as primary keys and separate unique fields for business identifiers.
- Index foreign-key columns and fields used by frequent queries, including `orders(status, reserved_until)` for the timeout job.
- Apply unique constraints to business identifiers such as usernames, role codes, product SKUs, and order numbers.
- Apply composite unique constraints to `user_roles(user_id, role_id)`, `order_items(order_id, product_id)`, `orders(user_id, idempotency_key)`, and `inventory_transactions(order_id, inventory_id, type)`. Create each as one composite key and never as separate unique keys on its columns. The physical ERD writes each one in the key column, for example `UK(user_id, idempotency_key)`.
- Require quantities to be greater than zero, stock quantities to be greater than or equal to zero, and monetary values to be non-negative.
- On `inventory_transactions`, require `quantity > 0`, require the four snapshot fields to be greater than or equal to zero, and require that `order_id` is present exactly when `type` is `RESERVE`, `CONFIRM`, or `RELEASE`. Enforce these with `CHECK` constraints and validate them in the service layer.
- Use fixed-precision decimals for money.
- Keep transaction prices and product snapshots in `order_items` unchanged after order creation, and do not edit or delete order items after the order is created.
- Place order creation, order-item creation, stock reservation, and reservation transactions in the same transaction boundary. Do the same for confirm and release.
- Treat inventory transactions, ticket assignment history, and operation logs as append-only trace data.
- Use controlled status values. Enforce valid state transitions in the service layer, and enforce the order status gate with the conditional update described above.
- Include `created_at` and `updated_at` on primary business tables. Historical and frozen tables, such as `inventory_transactions` and `order_items`, must include at least a creation timestamp.
- Do not cascade deletes into history. Foreign keys from `inventory_transactions`, `ticket_assignment_history`, and `operation_logs` must not delete or null out history rows.

## Future Evolution

The initial model is intentionally limited to the current system scope. Extend it only when the corresponding capability is introduced:

- Add `warehouses` and model inventory by product and warehouse when multi-warehouse support is required.
- Add a separate reservation table if one order needs partial confirmation, partial release, or reservations that outlive their order.
- Add payment and refund entities and a payment-driven order status when financial processing enters the system boundary.
- Add address, shipment, and package entities when fulfillment is implemented.
- Partition or archive generic audit data when log volume and query patterns require it.

When a field or relationship changes, update the migration history, physical ERD, and this document together.
