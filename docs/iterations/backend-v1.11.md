# Backend Iteration v1.11

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.11 |
| Date | 2026-11-16 |
| Baseline | [backend-v1.10.md](backend-v1.10.md) |
| Type | The ticket module and the audit log page of Phase 6, built against [api-v1.9.md](api-v1.9.md) |
| Trigger | Phase 6 in the backend: tickets (T-12, T-14, T-21, T-33, T-35, T-36, T-39, T-40, T-44, T-47, T-50, T-67), and the ticket races of T-17. Work on the branch `phase-6-backend` |

## Summary

- A new module, `ticket`, opens a ticket and makes its eight changes (take, assign, reassign, release, resolve, cancel, archive one, archive a batch), each in one transaction through the status gate, with the user rows locked before the ticket row, the limits of 99, the history row, and the log row.
- Who sees which ticket is asked in one place, `TicketAccess`, by the list, the detail, and every change, so an operator cannot reach by one path a ticket that another path hides.
- The list of the five screens is read by key with `SYSTEM` tickets first in either direction, with the search of the screen for the roles that may search.
- The archive job archives a resolved ticket after 7 days, and all of them at 99, as the SYSTEM identity.
- The cascades of the other modules are in place: disabling or deleting a user returns their tickets in progress to the pool (T-21), and deleting an item or delisting a product cancels its live tickets (T-39), each in the transaction of that change, as the SYSTEM identity.
- The user picker `GET /user-options` is in `user`.
- Two permissions: `TICKET_ASSIGN_PROTECTED` (administrator) and `TICKET_HANDLE_ANY` (administrator, manager). The limits of a description (4,000) and a resolution (2,000) are in `Limits`.
- The stock movements now take their time from the clock of the application, as a `SALE` does, which two `LogTest` checks needed when the database clock differed.
- The audit log page in the backend, with the time of every audit row from the clock of the application.
- 42 new tests (37 of the tickets, 5 of the audit page). The last full run: 432 passed, 0 failed.

## Changes

| Area | Change |
| --- | --- |
| `ticket` | New module: `TicketController`, `TicketService`, `TicketWriter`, `TicketAccess`, `TicketStatus`, `TicketCascades`, `TicketArchiveJob`, `TicketNumbers`, and `TicketRepository` |
| `user` | `UserChangeWriter.disable` and `softDelete` return the user's tickets in progress to the pool, and so does `replaceRoles` when the user loses every role that may take tickets (2026-11-17, [database-v1.17.md](database-v1.17.md)); its answer carries the number, and `GET /users/{id}/role-change` says it before. `UserOptionController` and `UserService.options` for the user picker |
| `warehouse` | `ItemWriter.delete` and `ProductWriter.delist` cancel the live tickets of the item, and write their ids in `cancelledTicketIds` and their numbers in `cancelledTicketNos`, so the audit page names them without a join (the owner's choice after the review of the Figma design) |
| `log` | The audit log page (T-48): `GET /audit-records` and `GET /audit-order-records` in `LogController`, `LogService`, and `LogRepository`, read through the indexes of Audit log page |
| `common`, audit time | `AuditLogWriter` writes `created_at` from the clock of the application, as the changes it records do |
| `common` | `Permission.TICKET_ASSIGN_PROTECTED` and `TICKET_HANDLE_ANY`, granted in `Role`; `ErrorCode.TICKET_CHANGED` and `NOT_ELIGIBLE`; `AuditLogWriter.TARGET_TICKET`; `Limits.TICKET_DESCRIPTION` and `TICKET_RESOLUTION`; `OpsflowProperties.Ticket` |
| Configuration | `opsflow.ticket.*` in `application.yml`: the number attempts, the two limits of 99, the items of a ticket, the size of a batch archive, and the delay, count, interval, and batch of the archive job |
| `warehouse`, time of a movement | `WarehouseRepository.insertTransaction` takes the time from the caller, and `ItemWriter` and `StockWriter` pass the clock of the application. It left `created_at` to the column default, so a restock was dated by the clock of the database and a `SALE` by the clock of the application; with the two clocks apart, two `LogTest` checks of their order failed in both full runs of this iteration |
| Click-through of 2026-11-17 (T-68, T-69, T-70) | `TicketRepository.list` filters by one item; `AuditAction` carries a fixed remark for each action and `AuditLogWriter` writes it when a change gives none; a take writes "Taken from the pool" in the history; `TicketCascades.returnToPool` answers the numbers, which `UserChangeWriter` writes as `returnedTicketNos`; `UserRepository.list` takes the status buttons, lists deleted users on request, and counts the tickets in progress of each row; `LogRepository.auditRecords` filters by the user a row is about. Two tests in `TicketTest` |
| Test support | `MySqlIntegrationTest` empties the tables with `DELETE` and sets the next ids back to 1, in place of `TRUNCATE`, and then checks that every table holds only the seed rows. The whole suite went from about 740 seconds to about 235, with the same 435 tests passing. Found on 2026-11-17: the earlier check of a clean start looked at the users only, so a table left with rows went unnoticed; a deliberate test that skipped `user_roles` proved the old check blind and the new one loud. The tests of one module are run while a change is made, and the whole suite before every commit (Tests in the backend design) |
| Tests | `TicketStatusTest`, `TicketIntegrationSupport`, `TicketTest`, and `TicketRaceTest`. `LimitsMatchSchemaTest` checks that the limit of a `TEXT` column fits it. The tests that build `OpsflowProperties` by hand take the new group |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the cascades live | In the ticket module, `TicketCascades`, called by the user and warehouse writers inside their transactions (`MANDATORY`) | In each calling module: the ticket rules and the ticket SQL would be in three places, and a module may not use another's repository |
| How the cascade of an item reads its tickets | `FOR UPDATE OF` the tickets and `FOR SHARE OF` the links, after the item or product is locked | `FOR UPDATE` on both tables: the application account has no `UPDATE` on the append-only links, and MySQL asks for it |
| A take that finds the ticket gone from the pool | `TICKET_CHANGED`, with `ticketStatus` only for a caller who may see the ticket now | `NOT_FOUND`, which the visibility rule gives: the person saw the ticket a moment ago in the pool, which is open to everyone, and should be told to reload |
| The roles that decide an assignment | Read again under the lock of the actor's user row, as the user module does | The roles of the request: a manager demoted a moment ago could still assign |
| Release, resolve, and cancel by a manager | `TICKET_HANDLE_ANY`, a permission of its own | `TICKET_ASSIGN`, held by the same roles today: a role that may assign but not cancel would get both |
| An operator who acts on a ticket they cannot see | `NOT_FOUND`, before any other rule | `NOT_ALLOWED`: it would tell the operator that a ticket with that id exists |
| The SYSTEM identity and the limits | Exempt from the open limit when it creates a ticket, and never an assignee | Counting it: it would one day stop the program from opening its own tickets |
| The archive job when both rules apply | The count of 99 first, then the delay, in one run, all rows of the run sharing one `batchId` | Two jobs: the same work twice a minute |
| A change of roles that takes away the right to take tickets | Returns the tickets in progress to the pool, in the transaction of the change, like a disabling (the owner's choice, 2026-11-17) | Leaving them with a person who can neither resolve nor release them until a manager notices |
| How the tables are emptied before a test | `DELETE` and the next id set back to 1, then a count of every table | `TRUNCATE`, which rebuilt the 17 tables before each of about 380 integration tests, two thirds of the whole run |
| The times of the ticket rows | Taken from the clock of the application and written explicitly, also the history and the links | The default of the column: the database clock and the application clock can differ, which the test runs of this iteration showed for the stock movements (see Changes) |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated: Change Log, Scheduled Jobs, Rules Checked By Tests, Tickets (new), and Configuration |
| [API design](../api/api-design.md) | Updated in [api-v1.9.md](api-v1.9.md): `TICKET_HANDLE_ANY` in `NOT_ALLOWED`, the `id` of a skipped ticket, and the name of the cause |
| [Database design](../database/database-design.md) | Updated: the ticket parameters in Operating Parameters. The tables did not change, so there is no migration and no change to the ERD |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: Tickets under Application tests, the latest run, the repeats of D5, and What is not tested yet |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated: the finished ticket tasks removed, and the frontend parts kept |
| [README.md](../../README.md) | Updated: Project Log |
| `db/verification/run-db-verification.ps1` | Not run: the schema, the privileges, and the flows of the script did not change |

## Open Items

| Item | Tracked in |
| --- | --- |
| Two orders that lock the same items in opposite order (D6) against the real code | T-17 |

The frontend of this iteration, the ticket screens and the audit log page, is built in [frontend-v1.9.md](frontend-v1.9.md).
