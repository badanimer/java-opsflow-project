# API Iteration v1.12

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API design and understand how it evolved.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.12 |
| Date | 2026-11-19 |
| Baseline | [api-v1.11.md](api-v1.11.md) |
| Type | The format of a ticket list row, and a filter of the audit page. No path, result code, or status code is new |
| Trigger | The owner's request of 2026-11-19 ([database-v1.20.md](database-v1.20.md)) |

## Summary

- A row of `GET /tickets` carries `person` and `time`: the person and the time of the step its screen is about. `createdBy`, `createdAt`, `closedAt`, and `cancelledAt` are gone from the row; the detail keeps them all.
- The list is ordered by `time`, and `from` and `to` filter on its day.
- `GET /audit-records` takes `ticketNo` in the area `TICKET`: the rows of that ticket. Another area, or a number that is not 8 letters and digits, is `VALIDATION_FAILED`; a number that no ticket has answers an empty page.

## Changes

| Section | Change |
| --- | --- |
| Change Log | The row of 1.12 |
| Tickets | The row of `GET /tickets`; the new note The person and the time of a row; The list order; the day of the search |
| Logs | `ticketNo` in the row of `GET /audit-records`, and its refusals |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The fields of a row | Two general fields, `person` and `time`, whose meaning the screen gives | One field for each step (`assignee`, `assignedAt`, `resolvedBy`, …) on every row, most of them empty on any one screen |
| The old fields | Removed: the only client is the page of the same repository, changed in the same step | Kept beside the new ones |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design](../api/api-design.md) | Updated |
| [Backend design](../backend/backend-design.md) | Updated in [backend-v1.16.md](backend-v1.16.md) |
| [Page design](../frontend/page-design.md) | Updated in [frontend-v1.13.md](frontend-v1.13.md) |

## Open Items

None.
