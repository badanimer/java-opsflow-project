# Backend Iteration v1.10

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.10 |
| Date | 2026-11-16 |
| Baseline | [backend-v1.9.md](backend-v1.9.md) |
| Type | The order module, the manual stock changes, and the logs of Phase 5, designed and built |
| Trigger | Phase 5 in the backend: orders and stock (T-10, T-12, T-30, T-41, and the first pass of T-17), then the logs (T-46). Work on the branch `phase-5-backend` |

## Summary

- A new module, `order`, places an order in one transaction: the products shared, the order and its items, the stock locked and deducted, one `SALE` per item, and one `COMPLETED` attempt per line.
- Every refused attempt is recorded after its rollback, in a transaction of its own, and answered with its `orderNo`. `BUSY` is recorded after the last retry.
- A key the user already used returns the original order. Two requests with one key at the same moment place one order.
- The three manual stock changes (stock-in, write-off, correction) are added to `warehouse`, each locking the `inventory` row.
- `Quantities` gains the ranges of a stock change, a correction, and an order line.
- A new module, `log`, reads the two logs, the deleted items, and the product picker, paged by key on the time in either direction, with the shared time filter `DayRanges` in Sydney days.
- 48 new tests. The last full run, after the page size of the product page: 395 passed, 0 failed.

## Changes

| Area | Change |
| --- | --- |
| [Backend design](../backend/backend-design.md) | New section Orders and Stock Changes: the classes, the write paths with their locks, how a refusal leaves the transaction, and the tests; Layout names the modules; Configuration has `opsflow.order.*` |
| Code | The package `order`: `OrderController`, `OrderService`, `OrderWriter`, `OrderAttemptRecorder`, `OrderRepository`, `OrderNumbers`. In `warehouse`: `StockService`, `StockWriter`, three endpoints in `ItemController`, and `setAvailable` and `isListed` in `WarehouseRepository` |
| Shared code | `Quantities.stockChange`, `countedQuantity`, `seenQuantity`, `orderLine`, and `asRecorded`; `STOCK_CHANGED` in `ErrorCode`; the group `order` of `OpsflowProperties` |
| Code, the logs | The package `log`: `LogController`, `LogService`, `LogRepository`; `DayRanges` in `common/time` |
| Paging | `opsflow.paging.product-page-size` (16, the default) and `product-page-max` (24), and `PageQueryFactory.createProductPage`, used by `GET /products`; `PageQueryFactoryTest` has a test of it |
| Configuration | `opsflow.order.max-lines` (20), `opsflow.order.number-attempts` (5), and `opsflow.time-filter.zone` (`Australia/Sydney`) in `application.yml` |
| Tests | `OrderTest` (12), `OrderRaceTest` (7), `StockTest` (9), `LogTest` (9), `DayRangesTest` (6), and `OrderIntegrationSupport`; `QuantitiesTest` from 3 to 7. Three tests that build `OpsflowProperties` by hand take the new groups |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the SQL of an order lives | In `order`, with its own reads of `products` and `inventory`, as Layout asks | Calling the warehouse: the order would lock rows through another module's repository, which `PackageDependencyRulesTest` forbids, and its lock order would be spread over two modules |
| Where the stock changes live | In `warehouse`, beside adding and deleting an item | In `order`: they are changes of an item on the stock page, not orders |
| How a refusal of the transaction reaches the record | `OrderWriter` throws `ApiException`, the proxy rolls back, and `OrderService` records the attempt with `OrderAttemptRecorder` (`REQUIRES_NEW`) and answers with the number | Writing the refused row inside the order transaction: the rollback would remove it |
| The record of a refusal | Best effort: a failure is logged and the refusal is still answered | Failing the request: the person would get an error for a refusal that was right |
| Two requests with one key at the same moment | The second insert waits on `uk_orders_user_idempotency`, fails when the first commits, and the service answers the first order | A read of the key inside the transaction: it cannot see an order that has not committed |
| The times of an order | The order after the product locks, the movements and the attempt after the stock locks, from the application clock | The default of the column: the time of a statement that waited would be the time it started |
| Where the logs live | A module of their own, `log`, that only reads | In `warehouse` and `order`: the product log reads tables of both, and a module may not use the repository of another |
| The cursor of a log | The creation time as the ISO text of the UTC wall time, and the id | A count of rows: paging by key is the rule of every list |
| `BUSY` in a test | A writer that always throws a lock wait, run through the real retry | A real lock wait: the test would wait for the timeout three times |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Orders and Stock Changes (new), Layout, Warehouse Items and Products (the count of `QuantitiesTest`), Configuration, and the Change Log |
| [API design](../api/api-design.md) | [api-v1.8.md](api-v1.8.md) |
| [Database design](../database/database-design.md) | [database-v1.16.md](database-v1.16.md): the refusals named, the order of the checks, and the times; no table or flow changed |
| Configuration and environment | No new environment variable. The new properties in Configuration. Backend design: Logs (new), Shared Code (`DayRanges`) |
| [Test log](../../db/verification/concurrency-tests.md) | Application tests: Orders and stock changes, `QuantitiesTest`, the latest `mvnw test` run, the repeats of D1 to D5, and What is not tested yet |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | T-10, T-30, and T-41 narrowed to the frontend; T-11 and T-12 to the ticket module; T-17 to D6 and the tickets; Phase 5 says what is built |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- The order dialog and the Modify dialog in the frontend, with their quantity checks (T-10, T-30, T-41).
- Two orders that lock the same items in opposite order, against the real code (T-17, D6).
