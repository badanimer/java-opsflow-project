# Frontend Iteration v1.12

## Purpose

This file records one iteration of the OpsFlow frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current page design and understand how it evolved.

The authoritative description is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.12 |
| Date | 2026-11-18 |
| Baseline | [frontend-v1.11.md](frontend-v1.11.md) |
| Type | One filter and one note on the order records |
| Trigger | Q-34, the last open detail of the page design, decided by the owner on 2026-11-18 ([database-v1.19.md](database-v1.19.md)). Work on the branch `phase-8-finish` |

## Summary

- **Ordered by** on the order records, for a role with `ORDER_VIEW_ALL` (manager, administrator, viewer): the user picker, "Everyone" when nothing is chosen, kept in the address, disabled while an order number is applied.
- **An operator** sees no such filter, and the line "You see only the orders you placed." under the filters. The server sends only their own records.
- The page design has no open detail left.

## Changes

| Area | Change |
| --- | --- |
| `auth/permissions.ts` | `ORDER_VIEW_ALL` |
| `api/logs.ts` | `RecordFilters.userId`, sent as `userId` except with an order number |
| `pages/logs/recordFilters.ts` | `userId` and `userName` in the address; a chosen person counts as a filter |
| `pages/logs/LogListPage.tsx` | The Ordered by picker on the order records with `ORDER_VIEW_ALL`, and the note without it |
| `text.ts` | `logs.everyone`, `logs.ownOrdersOnly`; the label reuses `logs.orderedBy`, the title of the column |
| `index.css` | `.log-filters__note` in the secondary text colour |
| Tests | `LogListPage.test.tsx`: the filter narrows the records to one person for a viewer, and an operator gets the note and no filter. `test/server.tsx` gives `ORDER_VIEW_ALL` to the roles that hold it |
| Figma (`Orders, Stock changes, and Logs`) | "Order records / First page": the Ordered by picker between the product and the start date, and its handoff note names `userId` and `ORDER_VIEW_ALL`. New frame "Order records / Operator, own orders only": one operator's orders, the operator's menu, no Ordered by, and the note. The other states of the order records keep the filter bar of the first page and were not redrawn |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| What the operator is told | One line under the filters | Nothing, which would let an operator think the list is everyone's |
| Where the filter sits | After the product, before the time, as on the audit page | A second row |
| The own log of a product | No change on the page: the server already sends only the operator's own records there | A note on that tab too |

## Verification

| Check | Result |
| --- | --- |
| `npm test` | 294 tests in 25 files, all passed (2026-11-18) |
| `npm run lint`, `npm run build` | Clean. The first build found a second `orderedBy` key in `text.ts`, removed in the same iteration |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design](../frontend/page-design.md) | Updated: Change Log, Logs, Open Details, the latest run |
| Figma file | Updated, as above |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
