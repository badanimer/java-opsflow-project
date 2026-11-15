# Backend Iteration v1.9

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.9 |
| Date | 2026-11-15 |
| Baseline | [backend-v1.8.md](backend-v1.8.md) |
| Type | The `warehouse` module of Phase 4, designed and then built |
| Trigger | Phase 4: warehouse items and products. Work on the branch `phase-4-backend` |

## Summary

- One module, `warehouse`, for items, their images, and products, because deleting an item delists its product and listing reads the stock under a lock.
- Five write paths, each with what it locks, what stops a concurrent request, and what it writes, in the lock order `products`, then `inventory`.
- Images are stored in the folder of `OPSFLOW_IMAGE_DIR` under random names, checked from their first bytes, and served without a token.
- The quantity rule becomes a shared class (`Quantities`), used first by the starting quantity.
- Built on 2026-11-15 with 37 tests; `mvnw test`: 347 passed, 0 failed.

## Changes

| Area | Change |
| --- | --- |
| [Backend design](../backend/backend-design.md) | New section Warehouse Items and Products: the classes, the write paths, the images, the lists, and the tests |
| Code | The package `warehouse`: `ItemController`, `ProductController`, `ImageController`, `ItemService`, `ProductService`, `ItemWriter`, `ProductWriter`, `WarehouseRepository`, `SkuGenerator`, `ImageStore` |
| Shared code | `Quantities` in `common/validation`; the groups `quantity` and `warehouse` of `OpsflowProperties`; the codes of [api-v1.7.md](api-v1.7.md) in `ErrorCode`; `TARGET_ITEM` and `TARGET_PRODUCT` in `AuditLogWriter`; `ApiExceptionHandler` answers a 413 with `IMAGE_TOO_LARGE` and a body that is not multipart with `UNSUPPORTED_MEDIA_TYPE` |
| Configuration | `opsflow.quantity.*`, `opsflow.warehouse.*`, and the multipart limit of 3 MB in `application.yml`; `backend/.gitignore` ignores `data/` |
| Tests | `ItemTest` (12), `ProductTest` (8), `ImageTest` (6), `WarehouseRaceTest` (8), `QuantitiesTest` (3), and `WarehouseIntegrationSupport`. `MySqlIntegrationTest` gives the images of every integration test a temporary folder; two tests that build `OpsflowProperties` by hand take the two new groups |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Modules | One `warehouse` module for items, images, and products | An `inventory` and a `product` module: the deletion of an item and the listing would each need the repository of the other, which `PackageDependencyRulesTest` forbids |
| Where the SKU is made | Before the transaction, again on a duplicate key, up to 5 attempts | Inside the transaction with a read of the existing SKUs: a read cannot see an insert that has not committed, so the key decides anyway |
| Where the upload is checked | In memory, at most 2 MB, before anything is written; Spring's multipart limit refuses a larger body first | Streaming to disk and checking afterwards: a wrong file would be on disk before it is refused |
| The image folder | `OPSFLOW_IMAGE_DIR`, by default `./data/images` | A folder in the jar or the web root: files would be lost on a rebuild, or served without the checks |
| The time of a deletion, a listing, and a delisting | Taken after the locks are held, as database v1.14 requires | Taken by the statement that waits |
| The tickets of a deleted item or a delisted product | Cancelled in Phase 6 with the ticket rules; the code keeps the place, with a reminder | Now: there are no tickets before Phase 6 |
| How a quantity reaches the check | Declared as `Object` in the request | `Integer`: the JSON reader turns `1.5` into `1` without a word, and refuses `"ten"` as an unreadable body instead of `INVALID_QUANTITY` |
| A body that is not multipart on the upload | No `consumes` on the method, and `MultipartException` answered `UNSUPPORTED_MEDIA_TYPE` | `consumes`: no method would match, and the permission layer refuses a request without a method as `FORBIDDEN` |
| An edit that changes nothing | Answered `200` with no log row | A log row for nothing |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Warehouse Items and Products (new), and the Change Log |
| [API design](../api/api-design.md) | [api-v1.7.md](api-v1.7.md) |
| [Database design](../database/database-design.md) | Not changed: the tables, the rules, and the locks of Phase 4 were already designed |
| Configuration and environment | `OPSFLOW_IMAGE_DIR` (optional) in [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) and in Configuration; the new properties in Configuration |
| [Test log](../../db/verification/concurrency-tests.md) | Application tests: Warehouse items and products, the latest `mvnw test` run, the repeats of D4 and D5, and What is not tested yet |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- Cancel the tickets of a deleted item or a delisted product with the ticket module (T-21, T-33).
