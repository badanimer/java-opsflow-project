# API Iteration v1.9

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.9 |
| Date | 2026-11-16 |
| Baseline | [api-v1.8.md](api-v1.8.md) |
| Type | New endpoints and result codes, designed before the backend of Phase 6, on the branch `phase-6-tickets` |
| Trigger | Phase 6, tickets (T-14, T-40, T-33, T-35, T-44, T-47), and the limits of the two `TEXT` columns (T-13), decided by the owner on 2026-11-16 |

## Summary

- The audit log page: `GET /audit-records` for the six areas of `operation_logs`, and `GET /audit-order-records` for Orders.
- The ticket endpoints: one list for the five screens with the search, the detail, opening a ticket, and eight changes (take, assign, reassign, release, resolve, cancel, archive one, archive a batch).
- The user picker `GET /user-options`, for the search filters and for choosing an assignee.
- Two result codes, `TICKET_CHANGED` and `NOT_ELIGIBLE`, and the meaning of `NOT_ALLOWED`, which was reserved for tickets.
- The limits of the two `TEXT` columns: a ticket description has at most 4,000 characters and a resolution at most 2,000. `Limits` has the two constants, and `LimitsMatchSchemaTest` checks that each fits its column.
- Every other text a person types already had its limit in the backend and in the page: the reasons of the stock changes (255) and the order remark (500).

## Changes

| Area | Change |
| --- | --- |
| General Rules | Limits: the two `TEXT` fields and their limits |
| Result codes | `TICKET_CHANGED` (409) and `NOT_ELIGIBLE` (409). `NOT_ALLOWED` (403) is the refusal of a rule of the ticket when the permission is held |
| Logs | The audit log page: `GET /audit-records`, one of six areas of `operation_logs` with the action, the time filter, and a user or System, and `GET /audit-order-records`, the Orders area from `order_attempts` with the result and the user |
| Users | `PUT /users/{id}/roles` answers `ticketsReturned`, and `GET /users/{id}/role-change` says how many tickets a new list of roles would return, for the warning of the Change roles dialog (2026-11-17, [database-v1.17.md](database-v1.17.md)) |
| Click-through of 2026-11-17 (T-68, T-69, T-70; [database-v1.17.md](database-v1.17.md)) | `GET /tickets` takes `inventoryId`; `GET /users` takes `status` (with `DELETED`) and answers `ticketsInProgress`; `POST /users/{id}/disable` answers `ticketsReturned`; `GET /audit-records` takes `aboutUserId` in the areas `ACCOUNT` and `ROLE`; `remark` is never empty, and a row names the tickets a change returned in `returnedTicketNos` |
| Tickets | New section: the endpoints, the ticket, who sees which ticket, the list order, the search, the user picker, the fields of a new ticket, and the refusals of each change |
| Database design | Two notes: the limits of the `TEXT` columns in Field Specifications, and `FORBIDDEN` in place of `NOT_ALLOWED` for a creator whose roles do not allow tickets, in Creating a ticket on items |
| Backend | `Limits.TICKET_DESCRIPTION` and `Limits.TICKET_RESOLUTION`, and the check of a `TEXT` column in `LimitsMatchSchemaTest` |
| Page design | The length limit of every text field, checked in the page with a counter on the long boxes; a reason on Assign; 0 to 20 items on Create ticket. The ticket pages are not built yet, so no frontend code changed |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The limits of a description and a resolution | 4,000 and 2,000 characters, the owner's choice. A long log pasted into a description is refused with the field, not cut | No limit: one request could fill 64 KB, and a list would carry it. A new migration to `VARCHAR`: the column can stay as it is |
| How the test checks a `TEXT` column | The limit times four bytes (`utf8mb4`) must fit the bytes of the column | Equality, which a `TEXT` column cannot give |
| The five screens | One `GET /tickets` with a required `status` | Five endpoints: the same rows, filters, and paging five times |
| A ticket the caller cannot see | `NOT_FOUND`, the same as a ticket that does not exist | `FORBIDDEN`, which would tell an operator that the number exists |
| A person in an answer | `{ id, username, system }`, the SYSTEM identity with `system` true | A null for the system: the page could not tell "nobody" from "the system" |
| Releasing | Carries `expectedAssigneeId`, compared under the ticket lock, like a reassignment | Only the status gate: a manager could release a ticket from someone who took it a moment after the page was read |
| A change that finds the ticket moved on | `TICKET_CHANGED` with `ticketStatus`, like `USER_CHANGED` | A code for each status: the page does the same in each case, it reloads |
| An assignee who may not hold the ticket | `NOT_ELIGIBLE` (409): the picker was read before, and a refresh shows the users again | `VALIDATION_FAILED`: the id is well formed, and the state of the user decides |
| A data rule of the ticket refused | `NOT_ALLOWED` (403), the code the design reserved for it | `FORBIDDEN`: the page could not tell a missing permission from a ticket that is not the caller's |
| Assigning to an administrator | A permission of its own, `TICKET_ASSIGN_PROTECTED`, held by `ADMIN` only (T-67), as with `ROLE_ASSIGN_PROTECTED` | Asking for the role `ADMIN` in the service: the code names permissions and never roles |
| Releasing, resolving, or cancelling any ticket | A permission of its own, `TICKET_HANDLE_ANY`, held by `ADMIN` and `MANAGER`, found while the backend was built: the matrix gives these rows a wide reach and a narrow one, and no other permission marks the wide one | `TICKET_ASSIGN`, which the same roles hold today: a role that may assign but not cancel would get both |
| The reason of an assignment from the pool | Required, as the Permission Matrix says, and kept in the history row | Optional: the matrix asks for it on both |
| The items of one ticket | 0 to 20, as the lines of an order | No limit: one request could lock many rows |
| A batch archive | 1 to 100 ids in one request, each in its own transaction, with the archived and the skipped in the answer | All `RESOLVED` tickets by one call: the person should see what is archived |
| The Orders area of the audit page | An endpoint of its own, `GET /audit-order-records`, with the rows of the order records | One endpoint for the seven areas: the rows of the attempts have other fields, and a typed page would read two shapes from one list |
| System on the audit page | `system=true`, which finds the rows with no operator; with a user it is refused | A user id of the SYSTEM identity: the rows of the system have no operator at all |
| The warning before a change of roles | A read of its own, `GET /users/{id}/role-change`, that the dialog asks when Save is pressed | A table of which role may take tickets in the frontend: only the server knows which role grants which permission |
| The item list of the form | `GET /items`, which every role that may create a ticket can read | A new endpoint for the same rows |
| The cancel message of a deleted or delisted item | `cancelCause` with the kind and the name of the item, built by the server | The page parsing `PRODUCT_DELISTED:5` and reading the product |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: General Rules, Result Codes And Status Codes, Tickets (new), and the Change Log |
| [Database design](../database/database-design.md) | Two notes, listed above. No table or field changed |
| [Page design](../frontend/page-design.md) | Notes, listed above. The Figma drawing of the ticket pages takes them when it is drawn, before the frontend of Phase 6 |
| Backend | `Limits` and `LimitsMatchSchemaTest`; the ticket module in [backend-v1.11.md](backend-v1.11.md) |
| [project-todo.md](../project-todo.md) | T-13: the limits are decided. T-67 added |
| [implementation-roadmap.md](../implementation-roadmap.md) | T-67 added to Phase 6 |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.

The backend of this design, the permissions of T-67 included, is built in [backend-v1.11.md](backend-v1.11.md).

The frontend of this iteration, the ticket screens and the audit log page, is built in [frontend-v1.9.md](frontend-v1.9.md).
