# Frontend Iteration v1.13

## Purpose

This file records one iteration of the OpsFlow frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current page design and understand how it evolved.

The authoritative description is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.13 |
| Date | 2026-11-19 |
| Baseline | [frontend-v1.12.md](frontend-v1.12.md) |
| Type | What a ticket list shows, and a filter of the audit page. Drawn in Figma and built |
| Trigger | The owner's request of 2026-11-19 ([database-v1.20.md](database-v1.20.md)) |

## Summary

- The ticket screens show the person and the time of their own step: Created by and Created in the pool, Assignee and Assigned in progress, Resolved by and Resolved, Archived by and Archived, Cancelled by and Cancelled. The list on a small screen shows the same person and time.
- The rows come in the order of that time, and the day filter of the search is on it.
- The Tickets area of the audit page has a Ticket number box: typed in any case and applied with Find or Enter, it combines with the action and the user to find one step of one ticket. A malformed number shows "Enter an 8-character ticket number."; clearing the box takes the filter off. The number is kept in the address (`/audit/tickets?ticketNo=K7Q2M9XA`).

## Changes

| Area | Change |
| --- | --- |
| `api/tickets.ts` | `TicketRow` has `person` and `time` |
| `api/audit.ts` | `ticketNo` in the filters, sent in the area `TICKET` only |
| `pages/tickets/TicketListPage.tsx` | The person and time columns from `TEXT.tickets.stepColumns` of the screen |
| `pages/audit/AuditPages.tsx` | `TicketNumberFilter` in the Tickets area, which follows the address when Clear filters changes it |
| `text.ts` | `stepColumns`, and the texts of the ticket number filter |
| Tests | `TicketListPage.test.tsx`: the In progress columns with the assignee and the time in Sydney, and the person column of Resolved, Archive, and Cancelled (4 tests). `AuditPages.test.tsx`: the ticket number with a malformed number, capitals, the action, and clearing; and no ticket number in another area (2 tests) |
| Figma | Section A "Ticket lists": the person and time headers of every In progress, Resolved, Archive, Cancelled, and Archive selected frame (72 cells), and the person cells that showed System on In progress and Resolved, where the SYSTEM identity cannot hold or resolve a ticket (30 cells). Section D "Audit log": a Ticket number field in every Tickets frame, and a new frame "Audit / Tickets / One ticket and one step" |
| Documentation | Page design: Change Log, Ticket screens, Audit log page, and the latest test run |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The column titles | The step of the screen: Assignee and Assigned, Resolved by and Resolved, Archived by and Archived, Cancelled by and Cancelled | One title for every screen, such as Handled by, which says less |
| How the ticket number is applied | Find or Enter, as the ticket number of the ticket screens; clearing the box takes it off | On every key, which would send a request for each half-typed number |

## Verification

| Check | Result |
| --- | --- |
| `npm test` | 300 passed in 25 files, 0 failed (2026-11-19); the run before had 294 |
| `npm run lint`, `tsc` | No problem |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design](../frontend/page-design.md) | Updated |
| [UI design in Figma](https://www.figma.com/design/68brMszRAYI4d261GKL1GU) | Updated: page "Tickets and Audit" |
| [Database design](../database/database-design.md) | Updated in [database-v1.20.md](database-v1.20.md) |

## Open Items

None.
