# Frontend Iteration v1.5

## Purpose

This file records one iteration of the OpsFlow page design and frontend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.5 |
| Date | 2026-11-15 |
| Baseline | [frontend-v1.4.md](frontend-v1.4.md) |
| Type | The frontend of Phase 3: the design rules set while its screens were drawn in Figma, and the build of the frame of the application and the user page in `frontend/`. No endpoint changed |
| Trigger | The backend of Phase 3 is done ([backend-v1.7.md](backend-v1.7.md), [api-v1.5.md](api-v1.5.md)); the owner reviewed the Figma design on 2026-11-15 |

## Summary

- A key decision is confirmed a second time: an action that cannot be undone, that takes access away at once, or that gives or takes away a protected role or every role. The page design lists where this is asked, for the user page now and for the later pages.
- The time filter has quick ranges, worked out in the page and sent as the two days of the existing filter.
- Two pages of the Figma file were drawn and reviewed: "App shell and Users" and "Shared components". The font stays the system font.
- Built: the frame of the application, whose navigation holds every page of the Page Map and shows each entry only with its permission; Not allowed and Page not found; the user page with its search, role buttons, and paging; and Change roles, Disable, Enable, and Delete with their confirmations and every answer of the server.
- Every control is shown by a permission from `GET /auth/me` and never by a role (T-27, frontend part, for the pages built so far).
- One signed-in device ([database-v1.14.md](database-v1.14.md)): a page whose account signed in on another device shows a dialog at once, and the login page says why. Drawn in Figma by the assistant, with the owner's agreement, and built.

## Changes

| Page | Before (v1.4) | Now |
| --- | --- | --- |
| Principles | No rule on confirmations | An action that is hard to take back asks for a second confirmation. A new section, Second Confirmation, gives the rule, how the dialog looks, and the table of actions |
| User page | Not settled which actions confirm | Delete and Disable confirm; Enable does not; Change roles confirms after Save when Administrator or Manager is added or removed, or when no role is left |
| Later pages | Not settled | Deleting an item, delisting a product, and archiving confirm. Cancelling, releasing, and reassigning a ticket, and the three stock changes, are confirmed by their reason dialog. Placing an order and signing out do not confirm |
| Logs, ticket screens, audit log page | A start day and an end day, or a start day until now | The same, and quick ranges: Last 1, 3, 7, or 15 days, or 1 or 3 months, ending today in `Australia/Sydney`. Changing a day by hand clears the quick range |
| Frame of the application | The top bar alone, over a placeholder page | The top bar, a menu on the left with every page of the Page Map by permission (pages not built yet are disabled entries), and a page header. Below 768 px the menu is in a drawer opened by a Menu button |
| Pages that cannot be opened | Any unknown address went to the home page | A known page without its permission says Not allowed; an unknown address says Page not found. Both offer Go to Products when the user may open it |
| Loading the account | A spinner; any failure said "Cannot reach the server" | "Loading your account…"; a fault of the server shows "Something went wrong" with its reference, and no answer shows "Cannot reach the server" |
| Home page | "You are signed in" with the roles | The placeholder of the product page: "Products are coming soon" |
| User page | Not built | Built as drawn: a table on a desktop and a stacked list below 768 px |
| Every signed-in page | No sign of another login | The dialog "You have been signed out" when the account signed in elsewhere: no close, no Cancel, one button Sign in again |
| Login page | Session expired was the only notice of an ended login | A warning: "You were signed out because your account signed in on another device. If this was not you, reset your password." |

## How the user page is built

| Part | How |
| --- | --- |
| Who sees which control | `userRules.ts`. Change roles needs `ROLE_ASSIGN`, and `ROLE_ASSIGN_PROTECTED` too on a user who holds Administrator or Manager. Disable, Enable, and Delete need `USER_MANAGE`, and `USER_MANAGE_PROTECTED` too on such a user. Nothing is offered on the viewer's own row, which is marked You. A viewer with neither `ROLE_ASSIGN` nor `USER_MANAGE` has no Actions column |
| Role buttons | Administrator and Manager are offered to a viewer with `USER_MANAGE_PROTECTED` or `ROLE_ASSIGN_PROTECTED`, the rule by which the server lists protected users. Operator, Viewer, and No role are offered to every viewer. They are toggle buttons (`aria-pressed`) |
| Search and paging | The search is sent on Enter and cleared with the box. A change of filter starts again at the first page. Previous and Next follow the cursors, and a page that a deletion emptied falls back to the first page |
| The roles offered in Change roles | The roles the viewer may give: Administrator and Manager only with `ROLE_ASSIGN_PROTECTED`. On a disabled user, the roles it does not hold are offered disabled, with the reason |
| Where a refusal is shown | `USER_CHANGED`, `NOT_FOUND`, and `FORBIDDEN` close the dialog, load the list again, and say why above it; `FORBIDDEN` also loads the account again, so a page the viewer may no longer open turns into Not allowed. `LAST_ADMIN`, `VALIDATION_FAILED`, a fault, and no answer keep the dialog open with the choice. `USER_NOT_ACTIVE` keeps it open, takes back the added roles, and offers removal only |
| After a change | The same page of the list is loaded again with the same filters, so the rows show what the server holds |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| When to ask for a second confirmation | When an action cannot be undone, takes access away at once, or changes a protected role or leaves no role | On every change: people learn to click through confirmations, and the one that matters is then ignored |
| Enabling a user | No confirmation: it gives back what was there, and Disable undoes it | A confirmation like Disable |
| Changing roles | A second step after Save only for Administrator, Manager, or an empty set | Always a second step: an ordinary change of Operator or Viewer is easily changed back |
| A dialog with a typed reason | Counts as the confirmation | A further dialog after it |
| The length of Last N days | N days including today: Last 1 day is today from midnight, Last 3 days starts two days ago | 24 or 72 hours back from now: the filter reads whole days (see [The time filter](../database/database-design.md#the-time-filter)), so a range of hours would need a new form of the filter |
| The length of Last 1 and 3 months | The same day 1 or 3 months back, plus one day: on 15 November, Last 1 month is 16 October to 15 November | Without the extra day: one day longer than a month, unlike the counting of Last N days. 30 and 90 days: not a month |
| Where the quick range is worked out | In the page, sent as the two days | A new parameter of the server: it would add a third form of the filter for the same result |
| The font | The system font, Ant Design's default, as on the account pages the owner accepted | Inter: a font file to ship, and the account pages would change too |
| Pages that are not built yet in the navigation | Shown disabled, to the users whose permissions will open them | Hidden: the shape of the application would change with every phase. A placeholder page: a page that says nothing |
| Where the role buttons come from | The permissions, as the server's list | The role names: a new role would need a change |
| The role buttons as a control | Buttons with `aria-pressed` | `Tag.CheckableTag`, as the Figma note says: it cannot be given a role, so a screen reader would not know it is a toggle. The look is the same |
| How the tests know the permissions of a role | A copy of the backend's `Role` table in `test/server.tsx`, for the tests only | A table in the application: the frontend would then decide by role, which T-27 forbids |
| The shared components for later pages (the reason dialog, the time filter, the user picker, the searchable picker) | Drawn now, built with the first page that uses them | Built now: code with no page to test it on |
| What the old device shows | A dialog over the page, then the login page (the owner's choice) | Going straight to the login page: what the person was reading would vanish without a word |
| When the old device learns it | At once, through the event stream, as mainstream products do (the owner's choice); also on any request and when the tab is shown again | Only on the next request: an idle tab would show the old page until a click failed |
| A `session-replaced` for a token that is no longer the stored one | Another tab of this browser signed in again: load the account, no dialog | The dialog: the person would be told they were signed out while they are signed in |
| Who drew the two frames | The assistant, in the Figma file, by copying the existing dialog and login states (the owner's choice) | Codex: a round trip for two frames |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design document](../frontend/page-design.md) | Updated: Purpose, Frontend Stack And Design Files (the font and the two new Figma pages), Running the Frontend Locally (the layout, the tests, the latest run), Principles, Second Confirmation (new), the time filter under Logs, Open Details, and the Change Log |
| [Database design document](../database/database-design.md) | Not changed: the quick range is sent as the two days of The time filter, and the user page follows its rules as they are |
| [API design document](../api/api-design.md) | Not changed: the frontend uses the endpoints of [api-v1.5.md](api-v1.5.md) as they are |
| Figma file | Pages "App shell and Users" and "Shared components", reviewed by the owner on 2026-11-15. Added later on 2026-11-15, with the owner's agreement: `Session / Signed in elsewhere` with its notes on "App shell and Users", and `Login / signed in elsewhere` on "Login and Sign-up" |
| Code under `frontend/` | The frame of the application, the user page, and the shared confirm dialog and pager, listed in Running the Frontend Locally. `npm test` on 2026-11-15: 143 tests in 10 files, all passed (136 before one signed-in device). The owner clicked through the frame, the user page with every role, and two logins of one account on two browsers against the running backend on 2026-11-15, with no fault found (T-64). `npm run lint` and `npm run build` passed |
| [README.md](../../README.md) | Updated: Project Log |
| [project-todo.md](../project-todo.md) | T-64 narrowed to the owner's check against the running backend |

## Open Items

- Build the reason dialog, the time filter, the user picker, and the searchable picker with the first page that uses each (T-30, T-46, T-47, T-48).
- When disabling or deleting a user returns their tickets to the pool (T-21, Phase 6), the text of the Disable confirmation says so.
