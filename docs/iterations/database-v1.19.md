# Database Iteration v1.19

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Database Design](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.19 |
| Date | 2026-11-18 |
| Baseline | [database-v1.18.md](database-v1.18.md) |
| Type | Who may read which rows of a log. No table, column, constraint, index, privilege, or migration changed |
| Trigger | Q-34. Every role read every order record, and nobody could filter them by who ordered except on the audit page. The owner decided on 2026-11-18 that the order records follow the ticket screens |

## Summary

- **An `OPERATOR` reads only their own order records**, in every way into them: all records, one product, the own log of a product or of a deleted item, and the search by order number.
- **`ADMIN`, `MANAGER`, and `VIEWER` read everyone's** and may narrow them to the person who ordered, chosen from the user picker. The filter is read through the existing `idx_order_attempts_user_created`.
- **The stock movements stay whole for every role**, other people's sales included, because an operator who corrects the stock needs every movement of the item.
- A new row in the Permission Matrix, and a fourth use of the user picker.

## Changes

| Section | Change |
| --- | --- |
| Change Log | The row of 1.19 |
| Logs | Every role reads the log of its page, with one limit; the new part Who sees which order records |
| Permission Matrix | New row under Orders: read everyone's order records and filter them by who ordered. `ADMIN`, `MANAGER`, `VIEWER` yes; `OPERATOR` only their own attempts, with no person filter |
| The user picker | Four places instead of three; a new row, the person filter of the order records, for `ADMIN`, `MANAGER`, and `VIEWER`, returning every user, disabled and deleted ones included |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Who sees which order records | As on the ticket screens: an operator their own, the other roles everyone's with a person filter (the owner's rule, 2026-11-18) | Every role sees every order, as before |
| The stock movements | Whole for every role, with who ordered each sale (the owner's choice) | Hiding who ordered the sales of others, or hiding those rows, which would leave an operator a stock history that does not add up |
| How the rule is held | One permission, `ORDER_VIEW_ALL`, checked by the service | A check of the role, which a new role would have to change |
| An index for the person filter | None new: `idx_order_attempts_user_created` serves it, as it serves the Orders area of the audit page | A new index |
| An order number with a person | Refused: the order number is used alone, as everywhere | Allowing both |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design](../database/database-design.md) | Updated: Change Log, Logs, Permission Matrix, The user picker |
| [API design](../api/api-design.md) | Updated in [api-v1.11.md](api-v1.11.md) |
| [Page design](../frontend/page-design.md) | Updated in [frontend-v1.12.md](frontend-v1.12.md) |
| [Backend design](../backend/backend-design.md) | Updated in [backend-v1.14.md](backend-v1.14.md) |
| Physical ERD, Simplified ERD, Business Process Flowchart | Not changed: no table, field, relationship, or flow changed |
| Migrations, [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql), `db/verification/run-db-verification.ps1` | Not changed and not run: the schema, the privileges, and the write flows did not change. The rule is a read rule of the service |
| [project-todo.md](../project-todo.md) | Updated: Q-34 removed |

## Open Items

None.
