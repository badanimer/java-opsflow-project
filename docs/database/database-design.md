# OpsFlow Initial Database Structure and Business Flow

## Purpose

This document defines the initial database boundaries, entity relationships, and core business flow for OpsFlow. It serves as the design baseline for implementation. It does not replace executable database migration scripts.

The initial model contains 12 tables across five domains: identity and access, product and inventory, orders, tickets, and auditing.

## Change Log

This document always describes the current design. Each iteration has a summary file in [`docs/iterations`](../iterations/). Entries are listed newest first.

| Date | Version | Summary | Details |
| --- | --- | --- | --- |
| 2026-10-14 | 1.1 | Added stock reservation, redefined order statuses, and aligned the document with the physical ERD | [database-v1.1.md](../iterations/database-v1.1.md) |
| Before 2026-10-14 | 1.0 | Initial database design baseline with 12 tables | Commit `7ab5f55` |

## Design Diagrams

| Diagram | Purpose | Link |
| --- | --- | --- |
| Simplified ERD | Provides a concise overview of the core entities and relationships | [Open Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) |
| Physical ERD | Defines implementation-oriented fields, keys, and relationships | [Open Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) |
| Business Process Flowchart | Describes the order, inventory, audit, and exception-ticket flow | [Open Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) |

The Simplified ERD and the Business Process Flowchart predate inventory reservation. Update them to match this document.

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
| `version` | Optimistic-lock counter, incremented on every change |

All three quantities must stay greater than or equal to zero. Stock still held in the warehouse is `available_quantity + locked_quantity`.

#### Inventory transaction types

| Type | Effect on `inventory` |
| --- | --- |
| `RESTOCK` | `available_quantity` increases |
| `ADJUST_IN` | `available_quantity` increases (manual correction) |
| `ADJUST_OUT` | `available_quantity` decreases (manual correction) |
| `RESERVE` | `available_quantity` decreases, `locked_quantity` increases |
| `CONFIRM` | `locked_quantity` decreases, `sold_quantity` increases |
| `RELEASE` | `locked_quantity` decreases, `available_quantity` increases |

`quantity` is always greater than zero; the type determines the direction. Each transaction stores `available_before`, `available_after`, `locked_before`, and `locked_after` so that any movement can be audited without replaying history. `order_id` is set for `RESERVE`, `CONFIRM`, and `RELEASE`, and is empty for stock-level operations. `operator_id` is empty for system actions such as timeout release.

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
2. If the key already exists for this user, the system returns the original order without changing anything.
3. The system validates that each product exists, is available for sale, and has a valid requested quantity.
4. Within one database transaction, the system:
   1. creates `orders` with status `CREATED` and a `reserved_until` deadline;
   2. creates the related `order_items`;
   3. reserves stock for each item with a conditional update, `available_quantity = available_quantity - q`, `locked_quantity = locked_quantity + q`, `version = version + 1`, guarded by `available_quantity >= q`, and treats zero affected rows as insufficient stock;
   4. inserts a `RESERVE` row into `inventory_transactions` for each item;
   5. writes the required `operation_logs`.
5. Items are processed in a fixed order, such as ascending inventory id, so that concurrent orders cannot deadlock each other.
6. The transaction commits only when every step succeeds. Insufficient stock rolls back the whole transaction and returns a clear result; no order is created and no stock changes.

### Confirm order

An operator or a confirmation endpoint completes the order.

1. In one transaction, update the order with `WHERE id = ? AND status = 'CREATED'` and require `reserved_until` to be in the future. Set `status = 'COMPLETED'` and `finished_at`.
2. If zero rows were affected, another request has already moved the order. Return the order's current state and stop. Do not touch inventory.
3. Otherwise, for each item, move stock from locked to sold with a conditional update guarded by `locked_quantity >= q`, and insert a `CONFIRM` transaction.
4. Write `operation_logs` and commit.

A confirmation that arrives after `reserved_until` is treated as a timeout and follows the cancel path.

### Cancel, time out, or fail an order

Cancellation by a user or operator, timeout, and definite business failure share one path.

1. In one transaction, update the order with `WHERE id = ? AND status = 'CREATED'`, setting the target status (`CANCELLED` or `FAILED`), `finished_at`, and `status_reason`.
2. If zero rows were affected, stop without touching inventory.
3. Otherwise, for each item, release stock with a conditional update guarded by `locked_quantity >= q`, and insert a `RELEASE` transaction.
4. Write `operation_logs` and commit.

A scheduled job finds orders with `status = 'CREATED'` and `reserved_until` in the past and cancels each one in its own transaction. Concurrent job runs are safe because only the transaction that wins the status update releases stock.

Transient database or interface errors must roll back the transaction and leave the order `CREATED` so the operation can be retried. They must never set `FAILED`. `FAILED` is reserved for a definite business outcome.

### Concurrency and duplicate protection

- The conditional status update is the single gate for every transition. Because only one transaction can move an order out of `CREATED`, confirm and cancel can never both succeed, and repeated requests cannot deduct or release stock twice.
- Inventory updates use guarded conditional statements and check the affected row count, so stock never goes negative.
- `UK(order_id, inventory_id, type)` on `inventory_transactions` is a second line of defense. A duplicate `RESERVE`, `CONFIRM`, or `RELEASE` for the same order and inventory record violates the constraint and rolls back.
- `UK(user_id, idempotency_key)` prevents duplicate order creation from repeated submissions.

### Exceptions

Situations that need manual investigation create `tickets`, which then move through assignment, investigation, reassignment, and resolution. The implementation will determine which failure categories automatically create tickets.

## Initial Constraints

- Use numeric `id` values as primary keys and separate unique fields for business identifiers.
- Index foreign-key columns and fields used by frequent queries, including `orders(status, reserved_until)` for the timeout job.
- Apply unique constraints to business identifiers such as usernames, role codes, product SKUs, and order numbers.
- Apply composite unique constraints to `user_roles(user_id, role_id)`, `order_items(order_id, product_id)`, `orders(user_id, idempotency_key)`, and `inventory_transactions(order_id, inventory_id, type)`.
- Require quantities to be greater than zero, stock quantities to be greater than or equal to zero, and monetary values to be non-negative.
- Use fixed-precision decimals for money.
- Keep transaction prices and product snapshots in `order_items` unchanged after order creation.
- Place order creation, order-item creation, stock reservation, and reservation transactions in the same transaction boundary. Do the same for confirm and release.
- Treat inventory transactions, ticket assignment history, and operation logs as append-only trace data.
- Use controlled status values. Enforce valid state transitions in the service layer, and enforce the order status gate with the conditional update described above.
- Include `created_at` and `updated_at` on primary business tables. Historical transaction tables must include at least a creation timestamp.

## Future Evolution

The initial model is intentionally limited to the current system scope. Extend it only when the corresponding capability is introduced:

- Add `warehouses` and model inventory by product and warehouse when multi-warehouse support is required.
- Add a separate reservation table if one order needs partial confirmation, partial release, or reservations that outlive their order.
- Add payment and refund entities and a payment-driven order status when financial processing enters the system boundary.
- Add address, shipment, and package entities when fulfillment is implemented.
- Partition or archive generic audit data when log volume and query patterns require it.

Before implementation, use the physical ERD to create the first executable database migration. When a field or relationship changes, update the migration history, physical ERD, and this document together.
