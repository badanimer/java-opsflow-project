# API Iteration v1.8

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.8 |
| Date | 2026-11-16 |
| Baseline | [api-v1.7.md](api-v1.7.md) |
| Type | New endpoints and one result code, designed and built in Phase 5, on the branch `phase-5-backend` |
| Trigger | Phase 5 in the backend: placing an order and the manual stock changes (T-10, T-12, T-30, T-41), then the logs, the Deleted items module, and the product picker (T-46) |

## Summary

- Four endpoints that change: `POST /orders`, and `POST /items/{id}/stock-in`, `/write-off`, and `/correction`.
- Six that read: the listing records and order records of the product log, the item records and stock movements of the warehouse log, the deleted items, and the product picker, with the time filter (`from`, `to`) and the search by one order number.
- One result code: `STOCK_CHANGED`, for a correction made from a stock that changed.
- Every recorded refusal of an order carries its `orderNo`. A key used before returns the original order with `200`.

## Changes

| Area | Change |
| --- | --- |
| Orders and Stock Changes | New section: the endpoints, the lines of an order, its number, what is and is not an attempt, the idempotency key, the quantities and the reason of a stock change, and the refusals of each |
| Logs | New section: the six lists, the three ways to read a log, the item picker, the time filter, the direction, and the order number |
| Paging | `GET /products` has its own page size: 16 by default and at most 24, so that the person can choose 8, 16, or 24 cards a page |
| Result codes | `STOCK_CHANGED` (409). `OUT_OF_STOCK` also refuses a write-off larger than the stock. `NOT_ALLOWED` is kept for the ticket module only |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The answer to a key used before | `200` with the original order; a new order is `201` | `201` both times: a client could not tell a repeat from a new order |
| A refusal of the permissions on an order | `FORBIDDEN`, from the permission check of every endpoint (T-27) | `NOT_ALLOWED`, which the design named before the permission check was built: a second code for the same case, and the order service would need a check of its own |
| The three stock changes | One endpoint each, named after the action, each with its permission | One endpoint with a `type`: the permission would depend on the body, and the check of the endpoints reads the method only |
| A write-off larger than the stock | `OUT_OF_STOCK`, as for an order | `INVALID_QUANTITY`: the number is valid, the stock is the problem, and a refresh can change it |
| A stock-in past the `INT` column | `INVALID_QUANTITY`, as Valid quantities says | A code of its own for a case that does not happen in use |
| A correction to the number the person saw | `VALIDATION_FAILED` on `quantity`, checked before the transaction | Taking the lock to find out: the request alone shows that it changes nothing |
| The most lines of one order | 20, an operating parameter (`opsflow.order.max-lines`) | No limit: one request could lock many rows. The product page sends one line |
| Where the logs live | Four lists at the top (`/listing-records`, `/order-records`, `/item-records`, `/stock-movements`), each with its kind of record | One endpoint per log with a `kind`: two kinds of record have different rows and different permissions |
| The own log of one product or item | The two lists of its kinds, read with its id | An endpoint of its own: it would return the same rows |
| The item picker | `GET /items`, which already lists every item that is not deleted, by name, with the SKU | A second endpoint for the same rows |
| The time filter | Days (`from`, `to`) in Sydney time, turned into UTC by the server | Times from the page: every page would have to know the zone and the day boundaries |
| An order number typed in another case | Written as the numbers are made (`ORD-` upper, the rest lower) before the exact match | Refused, or compared without regard to case: the column compares exactly, and a person reads the number from a screen |
| A quantity that is not valid and a product that is delisted, in one request | `INVALID_QUANTITY`: the quantity is checked first, without a transaction | Reading the products first: it would take locks for a request that is refused anyway |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: Result Codes And Status Codes, Orders and Stock Changes (new), Logs (new), and the Change Log |
| Backend | Built in [backend-v1.10.md](backend-v1.10.md) |
| Database design | The refusals named in [database-v1.16.md](database-v1.16.md) |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- The frontend of these endpoints: the order dialog on the product page, the Modify dialog on the stock page, the two logs with the time filter and the picker, and the Deleted items module (T-10, T-30, T-46).
