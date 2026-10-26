# Frontend Iteration v1.0

## Purpose

This file records one iteration of the OpsFlow page design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.0 |
| Date | 2026-10-26 |
| Baseline | None. This is the first iteration of the area |
| Type | A new area: the page design |
| Trigger | The owner asked for a page design document to start the UI design and to record the rough ideas. The main task was not to design the UI, but to adjust the database backwards from how the screens will be used |

## Summary

- A new document, [page-design.md](../frontend/page-design.md), collects the page design that was spread over the database design: the page map, what each role sees, and a first description of every page.
- The owner's rough design was added (and answered a second time the same day: the logs belong to the pages, the product log on the product page and the warehouse log on the stock page, each read as all records, one chosen, or the own log of one, with a shared searchable picker; and the first administrator is created with a console command): the product page as the home page, no order list, the stock page with its log, the ticket screens and their search, the user page, the audit log page, and the account pages.
- The rules that reach the database were put in the database design as [Screens and Lists](../database/database-design.md#screens-and-lists) and in the Permission Matrix, and the database was adjusted (see [database-v1.8.md](database-v1.8.md)). The page design depends on the database design and not the other way round.
- The visual design is not started and the framework is not chosen (T-18).

## Changes

- Added `docs/frontend/page-design.md` with a Change Log, the principles, the page map, a table of what each role sees, the description of each page, and the details still open.
- Added the Documentation Structure entry in `README.md`, a row in the Sync Map for page design changes, and the Project Log entry.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Where the page rules live | Rules that reach the data in the database design (Screens and Lists), appearance in the page design | All in one document, which would mix the data rules with the look of the pages |
| The first page | The product page for every role | A dashboard, which the owner does not want yet |
| Orders on the screen | None. An order is an action with a result | An order list, which the owner removed |
| What is left for later | Comments, notifications, a fuller paging system, and a fuller user system (all in future-ideas.md) | Building them now |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design](../frontend/page-design.md) | New |
| [Database design document](../database/database-design.md) | Updated: Screens and Lists and the Permission Matrix (see [database-v1.8.md](database-v1.8.md)) |
| [README.md](../../README.md) | Documentation Structure and Project Log updated |
| [documentation-sync-rules.md](../documentation-sync-rules.md) | A row added to the Sync Map |

## Open Items

- The visual design, the navigation, and the framework (T-18, Q-34).
- The details listed under Open Details in [page-design.md](../frontend/page-design.md) (Q-34).
