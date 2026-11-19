# Database Iteration v1.20

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current database design and understand how it evolved.

The authoritative description is [OpsFlow Database Structure And Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.20 |
| Date | 2026-11-19 |
| Baseline | [database-v1.19.md](database-v1.19.md) |
| Type | A column and two indexes on `tickets` (migration `V5`), the rule of what a ticket list shows, and a filter of the audit page |
| Trigger | The owner, clicking through the deployment on 2026-11-19: the In progress, Resolved, Archive, and Cancelled screens showed who created a ticket and when, where the screen is about who works on it, resolved it, archived it, or cancelled it, and when. A ticket the owner created and assigned to another person still showed the owner. The owner also asked for the audit page to find the record of one step of one ticket |

## Summary

- A row of each ticket screen shows the person and the time of the step the screen is about: the creator and the creation in the pool, the assignee who holds it and when they got it in progress, then who resolved, archived, or cancelled it and when.
- The list of each screen is ordered by that time, and the day filter of the search is on that time. Before, the In progress and Resolved screens were ordered by the creation.
- `tickets.assigned_at` holds when the current assignee got the ticket. A take, an assignment, and a reassignment write it with the assignee; a release empties both.
- Migration `V5__tickets_assigned_at.sql` adds the column, `idx_tickets_status_source_assigned`, and `idx_tickets_status_source_resolved`, and fills `assigned_at` for the tickets that have an assignee from the newest history row that gave it to them.
- The Tickets area of the audit page takes one ticket number, which combines with the action, the user, and the time.

## Changes

| Section | Change |
| --- | --- |
| Change Log | The row of 1.20 |
| Tickets (the tables) | `assigned_at` described with `assignee_id` |
| Ticket screens and who sees what | A list row: a table of the person and the time of each screen |
| Ticket list order | The time of each screen is the time of its step |
| Fields fixed after creation | `assigned_at` changes with the assignee |
| Ticket status | The take and the release set and clear `assigned_at` |
| Ticket search | The day filter is on the time of the screen; the index of each screen |
| Audit log page | The rows of one ticket |
| Concurrency by Module | The take, the reassignment, and the release write or empty `assigned_at` in their conditional update, with the time taken after the locks |
| Field Specifications | The assignment time |
| Initial Migration | `V4` and `V5` named |
| Initial Constraints | The two new indexes |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| What a row of each screen shows | The person and the time of the screen's step (the owner's decision) | The creator on every screen, as before |
| The Archive screen | Who archived it and when, System for the job (the owner's choice) | Who resolved it, with the archive time |
| The order and the day filter | The same time the row shows (the owner's choice), so the newest row is always on top | Only the shown columns, with the order by the creation, where a ticket taken today could sit below one taken last week |
| Where the assignment time is kept | A column `tickets.assigned_at`, with an index for the order (the owner's choice) | A subquery on `ticket_assignment_history` for each row, which cannot use an index for the order and the paging |
| `assigned_at` after a resolution or a cancellation | Kept, as the assignee is kept, so the row still says who held it | Emptied |
| The ticket filter of the audit page | The ticket number, combined with the existing action and user filters (the owner's request: "the person, the time, and the reason of one step of one ticket") | A filter by the person a ticket was given to |
| A ticket number that no ticket has | An empty page | `NOT_FOUND`, which would be the only filter that refuses a value that is well formed |

## Concurrency

No write path is new. The take, the assignment, the reassignment, and the release already change the ticket row in one conditional update under the lock of the ticket row; they now write or empty `assigned_at` in the same update, so the time always belongs to the assignee in the row. The time is taken after the locks, as every time of a change (database v1.15). The lock order does not change.

## Verification

| Check | Result |
| --- | --- |
| `V5` applied by Flyway in the test containers | Applied in every run of `mvnw test` |
| `V5` on real data | See [backend-v1.16.md](backend-v1.16.md#verification) |
| [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1) | See [backend-v1.16.md](backend-v1.16.md#verification) |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design](../database/database-design.md) | Updated |
| [`V5__tickets_assigned_at.sql`](../../backend/src/main/resources/db/migration/V5__tickets_assigned_at.sql) | New |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Updated: `tickets.assigned_at` after `assignee_id` |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Not changed: no table or relationship changed |
| [Business Process Flowchart](https://www.figma.com/board/At5VgWJp5LHbFl2qZYZzKn) | Not changed: the flow is the same |
| [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) | Not changed: no table added, and the application already has `UPDATE` on `tickets` |
| [API design](../api/api-design.md) | Updated in [api-v1.12.md](api-v1.12.md) |
| [Page design](../frontend/page-design.md) | Updated in [frontend-v1.13.md](frontend-v1.13.md) |

## Open Items

None.
