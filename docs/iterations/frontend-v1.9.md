# Frontend Iteration v1.9

## Purpose

This file records one iteration of the OpsFlow frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current page design and understand how it evolved.

The authoritative description is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.9 |
| Date | 2026-11-16 |
| Baseline | [frontend-v1.8.md](frontend-v1.8.md) |
| Type | The pages of Phase 6: their design in Figma, the review, and their build on the branch `phase-6-frontend` |
| Trigger | Phase 6: tickets and the audit log page (T-37, T-40, T-44, T-47, T-48), on the backend of [backend-v1.11.md](backend-v1.11.md) and [api-v1.9.md](api-v1.9.md) |

## Summary

- **Figma.** Codex drew the page "Tickets and Audit" (265 frames with their build notes, and a frame index). The assistant's review against the documents and the backend found four faults, which Codex corrected, and one leftover row, which the assistant corrected in the file.
- **The ticket screens**: one address for each of the five statuses, the system tickets first on a light blue row, the direction, the search of the screen with a ticket number or an order number used alone, and the batch archive on Resolved.
- **The ticket detail**: the content, the information, and the assignment history; the buttons of the status and the permissions; one dialog for each action; and the message of a cancellation by the system.
- **Create ticket**: a page of its own, with the items chosen from the searched list and every field checked before it is sent.
- **The audit log page**: the menu of seven areas, and each area with its filters and its rows read by their labels.
- The navigation now opens every page of the Page Map.
- 41 new tests, and one test of the navigation changed. The latest run, on 2026-11-17: 290 tests in 24 files, all passed; the lint and the build are clean.

## Changes

| Area | Change |
| --- | --- |
| Figma | The page "Tickets and Audit": section A, the five screens for every role with the search, the order, the paging, and the batch archive; B, the detail of each status for every role and the dialogs of every action in every state; C, Create ticket; D, the audit log; E, the navigation of each role. The page "App shell and Users" has the navigation of the five kinds of role as well |
| Figma, "App shell and Users" | Two frames added by the assistant on 2026-11-17, each with its build note: "Change roles / Confirm ordinary change" and "Change roles / Confirm with tickets returning" |
| Pages | `pages/tickets/`: `TicketListPage.tsx`, `TicketDetailPage.tsx`, `TicketActionDialog.tsx`, `CreateTicketPage.tsx`, `ticketState.ts`, `ticketParts.tsx`; `pages/audit/`: `AuditPages.tsx` and `auditRows.ts` |
| Components | `UserPicker.tsx`, the user picker in its two uses, with the choice System for the audit page |
| API | `api/tickets.ts` and `api/audit.ts`. `ApiError` keeps the other fields of the error body in `extras`, for the items named by `ITEM_CHANGED` |
| Permissions | The ticket permissions of the backend, `TICKET_ASSIGN_PROTECTED` and `TICKET_HANDLE_ANY` included, and the copy of the roles in the tests |
| Tests | `TicketListPage.test.tsx` (10), `TicketDetailPage.test.tsx` (16), `CreateTicketPage.test.tsx` (5), `AuditPages.test.tsx` (5), with `ticketFixtures.ts`; `AppLayout.test.tsx` checks that every entry opens. `testTimeout` raised from 15 to 30 seconds |
| User page | Change roles asks the server how many tickets in progress the new roles would return (`GET /users/{id}/role-change`) when Save is pressed; when some would, the second confirmation says how many, and the message after the change says how many went (2026-11-17, [database-v1.17.md](database-v1.17.md)). A test in `UserChanges.test.tsx` |
| How the tests are run | `npx vitest related` or one folder while a change is made, and the whole suite with the lint and the build before every commit (Running the Frontend Locally) |
| Click-through of 2026-11-17 (T-68, T-69, T-70) | The item filter of the ticket search; the status buttons of the user page, the tag Deleted, and the warning of Disable and Delete about tickets in progress, with the number that went in the message of a disabling; About user on the Accounts and Roles areas of the audit page, and the tickets returned in the summary of a row. Four tests. The Figma frames of these controls are drawn after the code, by Codex, from the prompt of the same day |
| Routes and navigation | `/tickets/pool`, `/in-progress`, `/resolved`, `/archive`, `/cancelled`, `/tickets/new`, `/tickets/{id}`, `/audit`, and `/audit/{area}`. Every entry of the navigation is built, and the current page of a group is its child, not the group |
| Texts | `tickets` and `audit` in `text.ts`, with the wording of the Figma frames |
| Page design | Frontend Stack And Design Files, Running the Frontend Locally, Ticket screens, Ticket detail, Create ticket, Audit log page, and Open Details |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Create ticket | A page of its own (`/tickets/new`), Codex's proposal, accepted by the owner | A dialog: 20 chosen items and 4,000 characters do not fit, and the field errors would scroll away |
| The row of the audit page (Q-34) | Five columns, the time, the action, the user, the target by its label, and a summary, with View for the fields before and after; the owner accepted the Figma layout | The raw before and after data in the row |
| The cancelled tickets of a deleted item or a delisted product on the audit page | Their numbers, from `cancelledTicketNos`, which the backend added after the review (the owner's choice) | A count, which was all the ids allowed |
| The refusal of a release, a resolve, or a cancel of a ticket that left the operator | `NOT_FOUND`, with Back to list, as the backend answers it; found in the review, where the frames had `NOT_ALLOWED` | `NOT_ALLOWED`, which the backend never gives an operator who cannot see the ticket |
| A take of a ticket that just left the pool | "This ticket is no longer in the pool." with Reload ticket | Not found: the person saw the ticket in the pool a moment ago |
| The Assignee filter on the pool | Left out: no ticket in the pool has an assignee | The filter as drawn, which can only find nothing |
| The selection of a batch archive | Kept across the pages of Resolved, up to 100, and cleared by a change of the search | One page at a time: 10 tickets a page would make a batch of 99 ten requests |
| The order number of a new ticket | Checked in the page as `ORD-` and 32 letters or digits, in any case, and sent as typed; the server writes it in lower case | Refusing capitals: a person copies the number from a screen |
| The second confirmation of Change roles | Every change is asked again after Save, with each role added and removed (the owner's decision, 2026-11-17, from the click-through) | Only a change of Administrator or Manager or an empty set, as before: an operator made a viewer was saved by one click |
| The warning of a change of roles | Read from the server when Save is pressed, shown in the second confirmation, and a failed read lets the change go on | A table in the frontend of the roles that may take tickets |
| The time limit of a test | 30 seconds for every test (`testTimeout`), and 45 for the ticket and audit tests, whose screens with the whole search bar render slowly in the test DOM. The first full run with 15 seconds had 7 older tests fail on time under the load of the new files; on their own they passed | 15 seconds, which fails on a busy machine and would make continuous integration flaky |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design](../frontend/page-design.md) | Updated: the sections above and the Change Log |
| [Database design](../database/database-design.md) | Audit log page: the row layout points at the page design. No table, rule, or permission changed |
| Figma file "OpsFlow UI" | Page "Tickets and Audit" added, and frames of each role on "App shell and Users" (not tracked by git) |
| [project-todo.md](../project-todo.md) | T-37, T-40, T-44, T-47, and T-48 removed; Q-34 narrowed to the one detail left |
| [implementation-roadmap.md](../implementation-roadmap.md) | Phase 6 built; T-37 done |
| [README.md](../../README.md) | Updated: Project Log |
| Backend | Not changed in this iteration |

## Open Items

| Item | Tracked in |
| --- | --- |
| Whether an operator can filter the order records by who ordered | Q-34 |
