# Frontend Iteration v1.4

## Purpose

This file records one iteration of the OpsFlow page design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.4 |
| Date | 2026-11-14, extended on 2026-11-15 with the review of the filters |
| Baseline | [frontend-v1.3.md](frontend-v1.3.md) |
| Type | A change to what a role sees on four pages that are not built yet. No code under `frontend/` changed |
| Trigger | The review of the Permission Matrix with the owner, recorded in [database-v1.12.md](database-v1.12.md) |

## Summary

- An operator can also make a correction on the stock page: the person types the quantity that was counted.
- The creator and assignee filters of the ticket screens, and the choice of an assignee, use a picker that shows usernames only.
- An administrator sees their own account on the user page with no control to change it. Nobody can disable or delete their own account or change their own roles.
- A user who holds several roles sees and can use whatever any one of them allows.
- The four roles are fixed, so there is still no page for them.

## Changes

| Page | Before (v1.3) | Now |
| --- | --- | --- |
| Stock page | An operator saw a plus and a minus. A manager or an administrator also had a field for the new quantity | An operator, a manager, or an administrator chooses plus, minus, or Correction. The reason is required for all three |
| What Each Role Sees | "Setting the quantity directly": administrator and manager | "Correction": administrator, manager, and operator. A sentence on a user with several roles is added |
| Ticket screens | The creator and the assignee filters, with no word on where the names come from | They are chosen from the user picker |
| Stock page (2026-11-15) | No search | A search box for the name, and the rows ordered by name |
| Logs, ticket screens, audit log page (2026-11-15) | A range of days, newest first | The time filter: a start day to an end day, or a start day until now, with newest first or oldest first. The own log of one product or item has it too |
| User page (2026-11-15) | No search and no filter | A search box for the username, and role buttons of which several can be chosen: the roles the viewer may manage, and No role |
| Audit log page (2026-11-15) | A user filter | The user is chosen from the user picker or as System |
| Ticket detail | Assign and Reassign need an eligible assignee | The assignee is chosen from the user picker: an operator or a manager, and an administrator only when an administrator assigns |
| User page | All users for an administrator | The same, and the administrator's own row has no controls. A user may hold several roles |
| Not planned | A page for role definitions, done through the backend | A page for the roles themselves: the four roles are fixed |

## Decisions Made

The decisions are the owner's and are recorded with their alternatives in [database-v1.12.md](database-v1.12.md#decisions-made). One belongs to the pages only:

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The name of the third stock change on the screen | Correction | Set the quantity directly |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design document](../frontend/page-design.md) | Updated: Page Map (Not planned), What Each Role Sees, the stock page, the ticket screens, the ticket detail, the user page, and the Change Log |
| [Database design document](../database/database-design.md) | Updated in [database-v1.12.md](database-v1.12.md): the Permission Matrix, The user picker, the User page, and Manual stock changes |
| [README.md](../../README.md) | Updated: Project Log |
| Figma file | Not changed: the four pages are not drawn yet. The word Correction is used when the stock page is drawn |
| Code under `frontend/` | Not changed, and `npm test` was not run again |

## Open Items

- Draw and build the stock page with the three choices (T-30), the ticket screens with the user picker (T-44, T-47), and the user page (T-48), each when its backend part is done.
- Hide the controls a user cannot use, for the union of the user's roles (T-27).
