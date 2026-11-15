# Database Iteration v1.16

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Database Design](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.16 |
| Date | 2026-11-16 |
| Baseline | [database-v1.15.md](database-v1.15.md) |
| Type | Clarifications of the order and stock flows found while building them. No table, column, constraint, privilege, or flow changed |
| Trigger | Phase 5 in the backend: the order module, the manual stock changes, and the logs ([backend-v1.10.md](backend-v1.10.md), [api-v1.8.md](api-v1.8.md)) |

## Summary

- The refusals of the manual stock changes are named by their codes: `STOCK_CHANGED`, `OUT_OF_STOCK`, and `INVALID_QUANTITY`.
- The quantity of an order is checked before its transaction, and the order takes its times after its locks.
- An order the roles do not allow is refused with `FORBIDDEN` by the permission check, as on every endpoint.
- Two operating parameters: at most 20 lines in one order, and 5 attempts at an order number. The display time zone of the time filter is written down as a parameter too.
- The search by one order number keeps `ORD-` in upper case, and the item picker reads the rows of the stock page.

## Changes

| Area | Before (v1.15) | Now |
| --- | --- | --- |
| Manual stock changes | A correction from a stale stock is refused with "a stock changed, please reload result"; a write-off larger than the stock is "refused" | `STOCK_CHANGED` and `OUT_OF_STOCK`. A stock-in that would pass the `INT` column is `INVALID_QUANTITY`. A correction to the number the person saw is refused before the transaction |
| Create order | The quantity is checked in step 3.1, under the product locks | It is checked in step 1, before the transaction, because it does not depend on the data. Step 3.1 checks the status, the name, and the price |
| Create order, the times | Not stated | `orders.created_at` is taken after the product locks are held; the `SALE` rows and the attempt after the stock locks are held, by the rule of Listing and delisting: lock first, then take the time |
| The order dialog | `NOT_ALLOWED` when the account or its role does not allow orders | `FORBIDDEN`, from the permission check of every endpoint (T-27), and `ACCOUNT_DISABLED` for a disabled account |
| Order attempts | `NOT_ALLOWED` is not recorded | A refusal of the permissions is not recorded, nor a request whose shape is wrong, which is no attempt |
| Operating Parameters | No limit on the lines of an order; the generation attempts named SKUs and ticket numbers; the zone of the time filter only in its text | 20 lines in one order; the 5 attempts cover order numbers too; the display time zone, `Australia/Sydney` |
| Home page and paging | No page numbers; 10 rows a page on every list | The number of the page shown ("Page 3"), counted by the page, still with no total; on the product page the person chooses 8, 16 (the default), or 24 cards a page, and a full page fills its rows (the owner's request on 2026-11-16, after seeing 8 cards in a row and 2 in the next) |
| Logs, finding by order number | The box turns the number to lower case, because the numbers are lower case | The numbers start with `ORD-` in upper case: the number is written that way, and the rest in lower case, before the exact match |
| The searchable picker | The item picker offers every item that is not deleted | The same, and it reads the rows of the stock page (`GET /items`) |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the quantity of an order is checked | Before the transaction: a refused quantity takes no lock, and the check is the same as in Valid quantities | Under the product locks, as the design first said: the result is the same and the locks are held for nothing |
| The times of an order | The order after the product locks, its movements and its attempt after the stock locks | One time for all, taken before the stock locks: a stock change that committed while the order waited would be dated after a sale that started from its result |
| A refusal of the permissions on an order | `FORBIDDEN`, as on every endpoint | `NOT_ALLOWED`, a second code for the same case |
| An order number typed in lower case | Written with `ORD-` upper and the rest lower | Turning everything to lower case, as the text said: it would never match a number that starts with `ORD-` |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: Manual stock changes, Order attempts, Create order, The order dialog, Home page and paging, Logs, The searchable picker, Operating Parameters, and the Change Log |
| Migrations, Physical ERD, Simplified ERD, Business Process Flowchart | Not changed: no table, column, or flow changed |
| [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1), [`concurrency-fixtures.sql`](../../db/verification/concurrency-fixtures.sql), [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) | Not changed and not run again: the schema, the privileges, and the order and stock flows of the procedures are the same. The last run, on 2026-11-15, had 162 passed |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the application tests of the order module and the stock changes repeat D1, D2, D3, and the order rows of D4 and D5 |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
