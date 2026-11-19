# Database Iteration v1.21

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current database design and understand how it evolved.

The authoritative description is [OpsFlow Database Structure And Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.21 |
| Date | 2026-11-19 |
| Baseline | [database-v1.20.md](database-v1.20.md) |
| Type | A new action of the audit log. No table, column, or migration changed |
| Trigger | The owner, 2026-11-19: in the Tickets area of the audit page a take from the pool and an assignment by a manager were both "Ticket assigned", so the people who took tickets themselves could not be found apart from the tickets given to them |

## Summary

- A take from the pool writes `TICKET_TAKEN`, by the person who took it. An assignment from the pool by a manager or an administrator stays `TICKET_ASSIGNED`.
- The data of the two rows is the same; the action and `operator_id` tell them apart.
- Earlier takes stay `TICKET_ASSIGNED` with the remark `taken`.

## Changes

| Section | Change |
| --- | --- |
| Change Log | The row of 1.21 |
| Categories | `TICKET_TAKEN` among the actions of `TICKET` |
| Ticket logs | The take and the assignment from the pool in two rows of the table |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How a take is told apart | A new action, `TICKET_TAKEN` (the owner's choice) | The same action, told apart by the remark or by the actor being the assignee, which the action filter cannot find |
| The takes logged before | Left as `TICKET_ASSIGNED` with the remark `taken`: `operation_logs` is append-only, and the application account cannot change a row | A migration that rewrites the old rows, which would change the history the log exists to keep |

## Verification

| Check | Result |
| --- | --- |
| Application tests | See [backend-v1.17.md](backend-v1.17.md#verification) |
| [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1) | Not run: no schema, privilege, or flow of the script changed. The run of the same day passed 162 of 162 ([backend-v1.16.md](backend-v1.16.md#verification)) |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design](../database/database-design.md) | Updated |
| Migrations, ERDs, flowchart, privilege script | Not changed: no table, column, or flow changed. `operation_logs.action` is a `VARCHAR` with no list of values in the schema |
| [API design](../api/api-design.md) | Not changed: it names no action values; they are the table of Categories in the database design |
| [Backend design](../backend/backend-design.md) | Updated in [backend-v1.17.md](backend-v1.17.md) |
| [Page design](../frontend/page-design.md) | Updated in [frontend-v1.14.md](frontend-v1.14.md) |

## Open Items

None.
