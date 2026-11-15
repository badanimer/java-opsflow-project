# Frontend Iteration v1.8

## Purpose

This file records one iteration of the OpsFlow page design and frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.8 |
| Date | 2026-11-16 |
| Baseline | [frontend-v1.7.md](frontend-v1.7.md) |
| Type | The pages of Phase 5: their design in Figma, the owner's review, and their build on the branch `phase-5-frontend` |
| Trigger | Phase 5: orders, stock changes, and the logs (T-10, T-30, T-41, T-46), on the backend of [backend-v1.10.md](backend-v1.10.md) and [api-v1.8.md](api-v1.8.md) |

## Summary

- **Figma.** Codex drew the page "Orders, Stock changes, and Logs" (384 frames with their handoff notes) and the frames of each role on "Stock and Products". The owner's review found three faults, which were corrected: a column of internal ids in the listing and item records, sample SKUs and repeated item names that cannot exist, and a write-off refusal that showed the page's own check and the server's answer at once.
- **The order dialog.** A quantity from 1 to 9,999 by typing or with minus and plus; one idempotency key for each dialog, sent again by Try again after Busy or after no answer; every recorded result with its order number and a way to copy it; the stock never shown.
- **Adjust quantity.** Stock in, write-off, and correction by permission, with the stock before and after, a required reason, and Reload item after a changed stock or an unknown result.
- **The logs.** View records, the four lists of records with the picker, the time filter and its quick ranges, the direction, and the box for one order number; the own log of a product or an item as tabs; Deleted items, and the own log of a deleted item in four or two parts.
- Built and tested: `npm test` 249 passed.

## Changes

| Area | Before (v1.7) | Now |
| --- | --- | --- |
| Product page | Cards with Delist | Also Order (`ORDER_PLACE`), View on every card, and View records in the header |
| Stock page | Edit and Delete for a manager; no column of actions for the others | Also Adjust quantity (any of the three stock permissions) and View on every row, so every role has the column of actions; View records in the header |
| Logs | Not built | Seven pages, listed in How the logs are built |
| `api/client.ts` | — | A request can carry headers, and a refusal carries the `orderNo` of the body |
| `dayjs` | Used by Ant Design only | Declared in `package.json` at the version Ant Design installs, because the time filter builds its days with it |
| Navigation | Products was marked only on `/` | Also on the log pages under `/products/` |

## Decisions Made

| Topic | Decision | Rejected alternative |
| --- | --- | --- |
| A log | A page of its own with an address that keeps its filters (Codex's proposal, the owner agreed) | A drawer or a dialog over the page: it could not be reloaded or shared |
| The three stock changes | One dialog with a segmented control | Three dialogs: the person would choose before seeing the stock |
| The own log | Tabs, the one read most first: orders before listings, stock movements before item records (the owner, 2026-11-16, after using them) | Two lists one above the other: the page would hold two pagers. The history first, as first built: View is opened to see what moved |
| The order number and the other filters | The others are disabled while a number is applied and come back when it is cleared | Clearing them: the person would lose the filters set before |
| Retry of an order | Try again after Busy and after no answer, with the same key | No retry: the person would open a new dialog, with a new key, and could order twice |
| Retry of a stock change | Never by itself: Reload item, then the person enters the change again | Sending again: a stock change has no key, so a change that was made could be made twice |
| The reason box | A fixed height of three rows | Growing with the text: it measured itself at every key, which made the tests twice as slow without helping the person |
| The actions of a row in a table (the owner, 2026-11-16, after seeing them wrap onto three lines) | One line of text links, the most frequent first and the danger link last (Adjust quantity · View · Edit · Delete); the column takes the width its links need, and a table scrolls sideways on a narrow window. The same on the user page and in the lists of records. On a small screen the stacked rows may still wrap | A bordered Adjust quantity button among links in a column of 300 px, as first drawn: the links wrapped onto three lines |
| The spacing of the columns of a table (the owner, the same day) | A short column (picture, SKU, stock, status, time, amount) has a width; the column of a name, a product, an item, or a reason takes the room that is left and wraps to two lines, then ends in "…" with the whole text on hover; the column of actions is as wide as its links. Listing status is 150 px, with its note under the tag. A list of records scrolls sideways only below 900 px. The two lines are cut by CSS (`line-clamp`) | Sizing every column to its content: Listing status and Actions grew far apart and the table scrolled on a wide window. Ant Design's measured two-line ellipsis: it measures each text on every render, which made the tests twice as slow |
| The product card (the owner, the same day: the small Order button looked cramped) | The price larger; View and Delist as a row of small links; Order a full-width primary button at the foot of the card, as shops show their main action | A small Order button between the links, as first drawn |
| Where the person is in a list (the owner, the same day) | "Page 3" in bold between Previous and Next, centred at the foot of every list; a button with no page to go to is disabled rather than hidden, so the number does not move. The number travels with the request of the page, so Next adds one, Previous takes one away, and a new search or filter starts at 1 | "Page 3 of 12" or page numbers to click: they need the count of the rows, which the design never makes, and a page read by key cannot be jumped to |
| How many products a page holds (the owner, the same day: a page of 10 left a row of 8 and a row of 2) | The person chooses 8, 16 (the default), or 24 with three icons at the end of the search row (one square, four, nine; the one that is on is filled blue), and a new choice starts at page 1. Each choice is one size of card smaller than the one before, and the columns divide the page at every width of the list (a container query): from 900 px 4, 8, and 12 columns; from 500 px 4, 4, and 6; below that 2, 2, and 3. The small cards have a smaller name and price, the large ones a larger. (The first thresholds, 1140 and 1600 px, gave 16 and 24 the same 8 columns on the owner's screen with its display scaling, so the two choices looked the same.) | 10 cards in as many columns as fit: a row half empty on most screens. One fixed size of 24: the owner wanted the choice |
| How long a test waits for the page | 3 seconds for `findBy...` and `waitFor`, set once in `test/setup.ts`; the limit of a test stays 15 s | The default of 1 second: two tests of the own log failed only when all 20 files ran at once, and passed alone |
| The name on an own log after a reload | Found from its newest record, or from Deleted items by its SKU | A new endpoint for one product or item: the records already hold the name |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design document](../frontend/page-design.md) | Updated: the Figma pages, Stock page, How the logs are built, Open Details, Running the Frontend Locally, and the Change Log |
| Figma file | The page "Orders, Stock changes, and Logs" and the role frames on "Stock and Products", reviewed on 2026-11-16 |
| Code under `frontend/` | The pages and components above. `npm test` on 2026-11-16: 249 tests in 20 files, all passed; `npm run lint` and `npm run build` passed. Part one (the two dialogs) was committed first, as 1d11a0f |
| [README.md](../../README.md) | Updated: Project Log |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | T-10, T-30, and T-46 removed as built; T-41 left for later quantity fields; the owner's check, added as T-67, done and removed |

## Open Items

None. The owner clicked through Phase 5 against the running backend on 2026-11-16, asked for the changes of the layout recorded above, and accepted the result (T-67).
