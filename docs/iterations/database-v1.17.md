# Database Iteration v1.17

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Database Design](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.17 |
| Date | 2026-11-17 |
| Baseline | [database-v1.16.md](database-v1.16.md) |
| Type | One rule of the tickets added, and the changes Phase 6 made to this document recorded together. No table, column, constraint, privilege, or migration changed |
| Trigger | The owner asked what happens to the tickets of a person whose roles are changed. The answer was that they stayed with a person who could no longer move them. Phase 6 ([backend-v1.11.md](backend-v1.11.md), [api-v1.9.md](api-v1.9.md), [frontend-v1.9.md](frontend-v1.9.md)) had changed this document without an entry in its Change Log; this iteration records those changes |

## Summary

- **A user who loses every role that may take tickets gives back the tickets in progress**, as a disabled or deleted user does: in the transaction of the change of roles, as the SYSTEM identity, with a history row and a log row. A change that leaves such a role, such as a manager made an operator, changes no ticket.
- **The Change roles dialog says how many tickets will go back** before the change, from a read of the server, and the answer of the change says how many went.
- **Three requests of the owner from the click-through** (T-68, T-69, T-70): the tickets of one warehouse item as a search filter; no log row and no history row without a reason; and on the user page the status buttons with the deleted users, the warning of Disable and Delete about tickets in progress, the tickets returned named in the rows, and on the audit page the rows about one user.
- **Recorded with it, the changes of Phase 6 to this document**: the limits of the two `TEXT` columns, three ticket parameters, the ticket numbers in the log rows of a deletion or a delisting, two refusals of a new ticket, the layout of a row of the audit page, and the end of the note that the return of tickets was still to be built.

## Changes

| Section | Change |
| --- | --- |
| Steps of a change to a user | Step 6: a replacement of roles returns the tickets in progress when the user could take tickets before it and cannot after it. The note that the return was still to be built (T-21) is replaced by where it is built |
| Reassigning and releasing a ticket | "When an assignee's account stops being usable, or can no longer take tickets": the change of roles joins the disabling and the soft deletion; a role given again brings no ticket back; a change that keeps such a role changes nothing; the dialog asks the server first |
| Concurrency by Module, Tickets | The row of a disabled or deleted user covers a user who loses every role that may take tickets: the replacement locks the same user row, so a take for that user waits and then fails the check, or commits first and its ticket goes back |
| Ticket search (T-68) | One warehouse item is a filter that combines with the others, chosen from the item picker |
| Ticket logs (T-69) | No row without a reason: the fixed remark of each action, "Taken from the pool" in the history of a take, and `returnedTicketNos` in the rows of a change that returned tickets |
| User page (T-70) | The status buttons Active, Disabled, and Deleted; a deleted user listed only on request, with no control; the tickets in progress of each row |
| Audit log page (T-70) | About user, a fourth filter of the Accounts and the Roles and permissions areas |
| Field Specifications (Phase 6, recorded here) | The two `TEXT` columns have limits chosen by the application: 4,000 characters for a description and 2,000 for a resolution (T-13, [api-v1.9.md](api-v1.9.md)) |
| Operating Parameters (Phase 6) | Three rows: the tickets one run of the archive job archives (500), the items one ticket may link (20), and the tickets one batch archive may name (100) ([backend-v1.11.md](backend-v1.11.md)) |
| Product logs (Phase 6) | The `ITEM_DELETED` and `PRODUCT_DELISTED` rows carry `cancelledTicketNos` beside `cancelledTicketIds`, so the audit page names the cancelled tickets without a join (the owner's choice after the Figma review) |
| Creating a ticket on items (Phase 6) | An order exception with no order number is `VALIDATION_FAILED` before the transaction, not `ORDER_NOT_FOUND`; a creator whose roles do not allow tickets is `FORBIDDEN`, not `NOT_ALLOWED` |
| Audit log page (Phase 6) | How a row is laid out points at the page design, where Q-34 decided it ([frontend-v1.9.md](frontend-v1.9.md)) |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The tickets of a user who can no longer take tickets | Returned to the pool in the same transaction as the change of roles, like a disabling (the owner's choice, 2026-11-17) | Left with the user: an operator made a viewer can see the tickets but neither resolve nor release them, and nothing tells a manager |
| When "can no longer take tickets" applies | The roles before grant the permission to take a ticket and the roles after do not, decided under the lock of the user row | Any change of roles: a manager made an operator still works on the same tickets |
| How the dialog knows | It asks the server (`GET /users/{id}/role-change`) when Save is pressed, and shows the number in the second confirmation; a failed read lets the change go on, and the answer of the change gives the number | The frontend deciding from the roles: only the server knows which role grants which permission |
| The tickets of one item | A filter that combines with the kind, the people, and the days, over the items that are not deleted | A third way used alone, like the two numbers: a manager wants "the open tickets on A4 paper that olive opened" |
| A change that asks for no reason | The system writes a fixed remark for it, one for each action, kept in the table of actions | Asking a reason for every change: Disable, Delete, and Delist were decided to ask none, and a required text that people fill with a dot says less than a fixed one |
| Deleted users | Listed on the user page only when the button Deleted is chosen, with no control | A page of their own, or mixing them into the default list: the page is for the accounts that can be managed |
| Finding what was done to an account | About user on the Accounts and Roles areas of the audit page | A history on the user page: the audit page already holds and pages these rows |
| The Phase 6 changes to this document | Recorded in this iteration, the first after them | Leaving them recorded only in the API, backend, and frontend iterations, which the Change Log of this document does not point to |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design](../database/database-design.md) | Updated: the sections above and the Change Log |
| Migrations, ERDs, privileges | Not changed: no table, column, or grant changed |
| `db/verification/run-db-verification.ps1` | Not run: the schema, the privileges, and the order and inventory flows did not change |
| Backend | Built in [backend-v1.11.md](backend-v1.11.md): `UserChangeWriter.replaceRoles` and `GET /users/{id}/role-change`, with a test of each case and a race with a take |
| API | [api-v1.9.md](api-v1.9.md): `ticketsReturned` and the read of the warning |
| Frontend | [frontend-v1.9.md](frontend-v1.9.md): the warning of the Change roles dialog and the message after it |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
