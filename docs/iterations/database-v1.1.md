# Database Iteration v1.1

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Initial Database Structure And Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.1 |
| Date | 2026-10-14 |
| Baseline | v1.0, the initial database design as of commit `7ab5f55` |
| Type | Design iteration (ERD and documentation); no migration scripts exist yet |
| Table count | 12 (unchanged) |
| Status | Design baseline for the first executable migration |

## Summary

Version 1.1 reviews the Initial Physical ERD against the design document, fixes inconsistencies between them, and moves stock reservation into the first release. Order creation now reserves stock. Confirmation converts the reservation into a permanent deduction. Cancellation, timeout, or business failure releases it.

## Changes

### Inventory reservation (new in the first release)

- `inventory` keeps `locked_quantity`. `available_quantity` now means sellable stock excluding reservations, and `sold_quantity` means confirmed deductions.
- `inventory_transactions.type` is now `RESTOCK`, `ADJUST_IN`, `ADJUST_OUT`, `RESERVE`, `CONFIRM`, or `RELEASE`.
- `inventory_transactions.quantity_change` became `quantity` (always greater than zero). The single before/after pair became `available_before`, `available_after`, `locked_before`, and `locked_after`.
- `inventory_transactions.product_id` was removed. A transaction references its stock record through `inventory_id` only.

### Order lifecycle

- `orders.status` is now `CREATED`, `COMPLETED`, `CANCELLED`, or `FAILED`. The unused `PAID` status was removed because payment is outside the system boundary.
- New `orders` fields: `reserved_until`, `finished_at`, `status_reason`.
- Every status change goes through a conditional update (`WHERE status = 'CREATED'`), so confirm and cancel cannot both succeed and repeated requests cannot deduct or release stock twice.
- A transient database or interface error rolls back and leaves the order `CREATED`. It never sets `FAILED`.

### Constraints and data rules

- Composite unique keys: `user_roles(user_id, role_id)`, `order_items(order_id, product_id)`, `orders(user_id, idempotency_key)`, `inventory_transactions(order_id, inventory_id, type)`.
- `ticket_assignment_history.to_user_id` is nullable so that unassignment can be recorded.
- `user_roles.updated_at` was removed because link rows are not updated.
- `tickets.type` values are `ORDER_EXCEPTION`, `INVENTORY_EXCEPTION`, and `OTHER`.
- Soft-deleted `users` and `products` keep their unique values reserved (`username`, `email`, `sku`).
- Stock quantities must be greater than or equal to zero.

### Document alignment

- The document now uses `assignee_id`, matching the physical ERD (previously `assigned_to`).
- The relationship summary now covers all connectors in the physical ERD.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Reservation storage | Extend existing tables | A separate reservation table, deferred to future evolution |
| Order statuses | No payment status | Keeping `PAID` as a placeholder |
| Transaction to stock reference | `inventory_id` only | Keeping `product_id`, or both |
| Soft delete and unique values | Values stay reserved | `deleted_at` with adjusted unique keys |
| Manual stock adjustment | Two types, `ADJUST_IN` and `ADJUST_OUT` | One signed `ADJUSTMENT` type |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated and renamed from `initial-database-design.md` |
| [Initial Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Not updated; does not show reservation |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn?utm_source=chatgpt&utm_content=edit_in_figjam&oai_id=v1%2FtDcmP81m3SYAzncralkx17NKbn5enuMgAGNWsRIfEoNLjm99sa6yfR&request_id=d371b03f-7e6a-445b-802e-8bf638e4ca02) | Not updated; does not show reservation |

## Open Items

- Update the simplified ERD and the business process flowchart to show reservation, confirmation, and release.
- Decide the reservation timeout duration and which failures automatically create tickets.
- Set DECIMAL precision, indexes, and foreign-key delete rules in the first migration. The ERD does not capture them.
- Decide whether `ticket_comments` may be edited after creation.
