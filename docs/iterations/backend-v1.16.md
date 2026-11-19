# Backend Iteration v1.16

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.16 |
| Date | 2026-11-19 |
| Baseline | [backend-v1.15.md](backend-v1.15.md) |
| Type | The ticket list and the audit records of the `ticket` and `log` modules, and migration `V5` |
| Trigger | The owner's request of 2026-11-19 ([database-v1.20.md](database-v1.20.md)) |

## Summary

- `TicketRepository.list` reads the person and the time of the screen's step (`personColumn`, `timeColumn`): `created_by` and `created_at` in the pool, `assignee_id` and `assigned_at` in progress, `resolved_by` and `resolved_at`, `closed_by` and `closed_at`, and `cancelled_by` and `cancelled_at`. The list is ordered by that time, and the day filter is on it. `Listed` and the answer `TicketRow` carry `person` and `time`.
- `assignFromPool` and `reassign` write `assigned_at` with the assignee, the time taken after the locks; `release`, by a person or by the system, empties both.
- `GET /audit-records` takes `ticketNo` in the area `TICKET`: `LogService` checks it with `TicketNumbers.typed`, now public, and `LogRepository` reads the rows whose target is that ticket.
- Migration `V5__tickets_assigned_at.sql`.

## Changes

| Area | Change |
| --- | --- |
| `ticket` | `TicketRepository`: `assignFromPool`, `reassign`, and `release` write or empty `assigned_at`; `list` by `timeColumn` and `personColumn`; `Listed` with `person` and `time`. `TicketWriter`: the time of a take, an assignment, and a reassignment is taken after the locks and passed to the update. `TicketService`: the cursor from the time of the row. `TicketController`: `TicketRow` with `person` and `time`. `TicketNumbers.typed` is public |
| `log` | `LogController`, `LogService`, `LogRepository`: `ticketNo` on the audit records |
| Migration | `V5__tickets_assigned_at.sql`: `tickets.assigned_at`, `idx_tickets_status_source_assigned`, `idx_tickets_status_source_resolved`, and the times of the tickets that have an assignee, from the history |
| Tests | `TicketTest`: `eachScreenShowsThePersonAndTheTimeOfItsOwnStep` and `theInProgressAndResolvedScreensAreOrderedAndFilteredByTheirOwnTime`. `AuditTest`: `theTicketsAreaFindsTheRowsOfOneTicketByItsNumberWithTheOtherFilters`. `TicketIntegrationSupport.ticketRow` writes `assigned_at` for a ticket with an assignee |
| Documentation | Backend design: Change Log, Tickets, Logs. The test log: the three tests and the latest run |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the columns of a screen are chosen | Two switches on the status in `TicketRepository`, next to the SQL that uses them | A map in the service, away from the query |
| Using `TicketNumbers` from `log` | `typed` made public, so the form of a ticket number is written once | A second pattern in `LogService` |

## Verification

| Check | Result |
| --- | --- |
| `mvnw test` | 462 passed, 0 failed (2026-11-19); the run before had 459. Flyway applies `V5` in the containers of every run |
| `V5` on real data | Applied to the deployment by `docker compose up -d --build` on 2026-11-19 ("now at version v5"). Of its four tickets, the one taken and then reassigned got the time of the reassignment, the one taken got the time of the take, the open and the cancelled one stayed empty, and no `updated_at` changed. The two indexes exist, and `http://localhost:8000` answers 200 |
| [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1) | Not run yet: it needs the MySQL root password, typed by the owner in its masked dialog. It applies `V1` and `V3` only, so it does not apply `V5` itself; `V5` is checked by the two rows above |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated |
| [Test log](../../db/verification/concurrency-tests.md) | Updated |
| [Database design](../database/database-design.md) | Updated in [database-v1.20.md](database-v1.20.md) |
| [API design](../api/api-design.md) | Updated in [api-v1.12.md](api-v1.12.md) |
| [Environment](../environment-setup.md) | Not changed: no variable, account, or service changed |

## Open Items

None.
