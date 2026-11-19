# Backend Iteration v1.17

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.17 |
| Date | 2026-11-19 |
| Baseline | [backend-v1.16.md](backend-v1.16.md) |
| Type | A new audit action in the `ticket` module |
| Trigger | The owner's request of 2026-11-19 ([database-v1.21.md](database-v1.21.md)) |

## Summary

- `AuditAction.TICKET_TAKEN` (category `TICKET`, fixed remark "Taken from the pool").
- `TicketWriter.take` writes it; `assign` keeps `TICKET_ASSIGNED`. `logAssigned` takes the action as a parameter.

## Changes

| Area | Change |
| --- | --- |
| `common/audit` | `AuditAction.TICKET_TAKEN` |
| `ticket` | `TicketWriter`: `logAssigned(action, …)` |
| Tests | `TicketTest.takingMovesAnOpenTicketToInProgressAtOnceAndOnlyOnce` checks `TICKET_TAKEN` by the taker, and `anAssignmentGoesOnlyToAnEligibleAssigneeAndNeedsAReason` checks `TICKET_ASSIGNED`. `AuditTest.theTicketsAreaFindsTheRowsOfOneTicketByItsNumberWithTheOtherFilters` finds a take with `action=TICKET_TAKEN` and not with `TICKET_ASSIGNED`. No test was added |
| Documentation | Backend design: Change Log. The test log: what the two tests show |

## Verification

| Check | Result |
| --- | --- |
| `mvnw test` | 462 passed, 0 failed (2026-11-19), the same count as the run before: two tests check more, none was added |
| The deployment | The backend and the pages rebuilt with `docker compose up -d --build backend web` on 2026-11-19; `http://localhost:8000` answers 200. Its earlier takes stay `TICKET_ASSIGNED` |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated |
| [Test log](../../db/verification/concurrency-tests.md) | Updated |
| [Database design](../database/database-design.md) | Updated in [database-v1.21.md](database-v1.21.md) |

## Open Items

None.
