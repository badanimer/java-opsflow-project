# OpsFlow Initial Database Structure and Business Flow

## Purpose

This document defines the initial database boundaries, entity relationships, and core business flow for OpsFlow. It serves as the design baseline for implementation. It does not replace executable database migration scripts.

The initial model contains 12 tables across five domains: identity and access, product and inventory, orders, tickets, and auditing.

## Design Diagrams

| Diagram | Purpose | Link |
| --- | --- | --- |
| Simplified ERD | Provides a concise overview of the core entities and relationships | [Open Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) |
| Initial Physical ERD | Defines implementation-oriented fields, keys, and relationships | [Open Initial Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) |
| Business Process Flowchart | Describes the order, inventory, audit, and exception-ticket flow | [Open Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) |

## Table Overview

### Identity and Access

| Table | Responsibility |
| --- | --- |
| `users` | Stores user accounts, identity information, and account status |
| `roles` | Defines system roles |
| `user_roles` | Associates users with roles and supports multiple roles per user |

`users` and `roles` have a many-to-many relationship implemented through `user_roles`.

### Product and Inventory

| Table | Responsibility |
| --- | --- |
| `products` | Stores product details, prices, and sales status |
| `inventory` | Stores the current available quantity and concurrency-control data for each product |
| `inventory_transactions` | Records stock increases, deductions, rollbacks, and manual adjustments |

In the initial version, each product has one current inventory record, so `products` and `inventory` have a one-to-one relationship. A product can have many inventory transactions.

`inventory` represents the current state for efficient reads. `inventory_transactions` preserves the history required to explain why stock increased or decreased.

### Orders

| Table | Responsibility |
| --- | --- |
| `orders` | Stores order-level information such as the customer, order number, total amount, and status |
| `order_items` | Stores the purchased product snapshot, quantity, and transaction price |

A user can create many orders. Each order must contain one or more order items. Each `order_items` record references exactly one product. An order containing multiple products therefore contains one item record for each product.

- `users` 1:N `orders`
- `orders` 1:N `order_items`
- `products` 1:N `order_items`

An order item should retain the product name or SKU and price as they were when the order was created. Later product updates must not change the meaning of historical orders.

### Tickets

| Table | Responsibility |
| --- | --- |
| `tickets` | Stores exception tickets, the fixed creator, current assignee, status, and priority |
| `ticket_assignment_history` | Records every assignment and reassignment, including the previous and new assignees |
| `ticket_comments` | Stores investigation notes, communication, and resolution details |

The ticket creator is fixed in `created_by`. `assigned_to` contains only the current assignee, and a ticket has at most one current assignee at a time. Each assignee change creates a new `ticket_assignment_history` record instead of overwriting the assignment history.

A ticket may initially be unassigned and may be reassigned multiple times during its lifecycle. Each reassignment should record the previous assignee, new assignee, acting user, reason, and timestamp.

### Auditing

| Table | Responsibility |
| --- | --- |
| `operation_logs` | Records important business actions, actors, target objects, and relevant state changes |

The initial version uses a generic target reference. `target_type` and `target_id` identify an order, inventory record, ticket, or another business object. The application maintains this polymorphic relationship because it cannot be represented by one conventional foreign-key constraint.

## Relationship Summary

| Source | Cardinality | Target | Description |
| --- | --- | --- | --- |
| `users` | M:N | `roles` | Implemented through `user_roles` |
| `users` | 1:N | `orders` | A user creates multiple orders |
| `orders` | 1:N | `order_items` | An order contains at least one item |
| `products` | 1:N | `order_items` | Each item references one product |
| `products` | 1:1 | `inventory` | Each product has one current inventory record in the initial version |
| `products` | 1:N | `inventory_transactions` | A product produces multiple inventory transactions |
| `orders` | 1:N | `tickets` | An order can produce multiple tickets for separate exceptions |
| `users` | 1:N | `tickets` | Users participate as creators and current assignees |
| `tickets` | 1:N | `ticket_assignment_history` | A ticket retains its complete assignment history |
| `tickets` | 1:N | `ticket_comments` | A ticket contains processing notes and communication |
| `users` | 1:N | `operation_logs` | A user performs multiple audited operations |

## Order Creation Flow

1. An authenticated user submits an order containing one or more products.
2. The system validates that each product exists, is available for sale, and has a valid requested quantity.
3. The system validates inventory and uses conditional updates or locking to prevent overselling under concurrent requests.
4. Within one database transaction, the system creates `orders`, creates the related `order_items`, deducts `inventory`, and inserts `inventory_transactions`.
5. The system writes the required `operation_logs` for order and inventory operations.
6. The transaction commits only when every required operation succeeds. Any failure rolls back the transaction so that partial order or inventory data cannot remain.
7. Exceptions requiring manual investigation create `tickets`, which then move through assignment, investigation, reassignment, and resolution.

Insufficient stock is an expected business failure. It returns a clear result without changing inventory. Database errors and other system failures must roll back the transaction. The implementation will determine which failure categories should automatically create exception tickets.

## Initial Constraints

- Use numeric `id` values as primary keys and separate unique fields for business identifiers.
- Index foreign-key columns and fields used by frequent queries.
- Apply unique constraints to business identifiers such as usernames, role codes, product SKUs, and order numbers.
- Require quantities to be greater than zero and monetary values to be non-negative.
- Keep transaction prices and product snapshots in `order_items` unchanged after order creation.
- Place inventory deduction, order creation, and order-item creation in the same transaction boundary.
- Treat inventory transactions, ticket assignment history, and operation logs as append-only trace data.
- Use controlled status values and enforce valid state transitions in the service layer.
- Include `created_at` and `updated_at` on primary business tables. Historical transaction tables must include at least a creation timestamp.

## Future Evolution

The initial model is intentionally limited to the current system scope. Extend it only when the corresponding capability is introduced:

- Add `warehouses` and model inventory by product and warehouse when multi-warehouse support is required.
- Split stock into available and reserved quantities when inventory reservation is introduced.
- Add payment and refund entities when financial processing enters the system boundary.
- Add address, shipment, and package entities when fulfillment is implemented.
- Partition or archive generic audit data when log volume and query patterns require it.

Before implementation, use the physical ERD to create the first executable database migration. When a field or relationship changes, update the migration history, physical ERD, and this document together.
