# Frontend Iteration v1.2

## Purpose

This file records one iteration of the OpsFlow page design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current page design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.2 |
| Date | 2026-11-13 |
| Baseline | [frontend-v1.1.md](frontend-v1.1.md) |
| Type | The choice of the frontend stack, and the first visual design: four pages drawn in Figma |
| Trigger | The backend of login, sign-up, and password recovery is finished, and the owner started the frontend of that part on the branch `feature/frontend-auth` |

## Summary

- The frontend stack is chosen: React with TypeScript, built with Vite, with the components of Ant Design (T-18).
- The interface is in English, on every page.
- The login token is kept in the `localStorage` of the browser.
- Every page is drawn in Figma before it is built. The first four are drawn as a draft: Login, Sign-up, Forgot password, and the Pending page, with 28 states of them, after a second round with the owner.
- The frontend is built beside the backend, one module at a time, and not after it.

## Changes

| Area | Before (v1.1) | Now |
| --- | --- | --- |
| Framework | Not chosen (T-18) | React with TypeScript and Vite, in `frontend/` next to `backend/` |
| Components | Not chosen | Ant Design, with its default look as the starting point |
| Language of the interface | Not stated. The owner's first messages for the pages were in Chinese | English, for every text of the interface. The messages are the English ones of the page design |
| Where the token is kept | "Decided when the frontend starts" (backend design) | `localStorage`: the user stays logged in after a refresh, until the token expires after 12 hours |
| Visual design | Not started | The UI design file in Figma holds the drafts of the four account pages, a small set of local components (field, four buttons, four alerts, logo), and the colours as variables |
| Order of work | The frontend after the backend, or beside it: not decided | Beside it. Each page is drawn first, reviewed by the owner, and then built against the finished endpoints |

The draft has 32 frames of 1440 by 900. The first round had 14; the owner then changed the "too many" messages and asked for the invalid password and for every other case that needs a design:

| Page | Frames |
| --- | --- |
| Login | The empty page; wrong username or password; locked after failed attempts; account disabled; empty fields; signing in; after a password reset; session expired; something went wrong; cannot reach the server |
| Sign-up | The empty page; code sent, with the button for a new code disabled for 60 seconds; email already registered; wrong or expired code; username taken, with the code kept; too many requests; invalid username; invalid email; invalid password; a code that is not 6 digits; empty fields; sending the code; creating the account |
| Forgot password | The empty page; code sent, with the same sentence whether or not the address has an account; a new password equal to the current one; invalid password; wrong or expired code; too many requests; resetting |
| Pending | The page of a user with no role, with a button that checks again and a link to sign out; and the state after a check that still found no role |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The framework | React, the owner's choice | Vue, which the learning documents also mention |
| The language | TypeScript: a field name or a result code that is typed wrongly is found in the editor | JavaScript |
| The build tool | Vite | A larger framework such as Next.js: OpsFlow is a single-page application behind a login and needs no server rendering |
| The components | Ant Design: OpsFlow is an internal program with many tables, forms, and dialogs, and the library is made for that | Writing every component by hand, which is slow for a date picker or a searchable select |
| The language of the interface | English everywhere, the owner's decision | Chinese, as in the owner's first messages. A mix of both |
| Where the token is kept | `localStorage`. A refresh keeps the user logged in | Memory only: the safest, and a refresh logs the user out. `sessionStorage`: closing the tab logs the user out. A script injected into the page could read the token from `localStorage`; this is accepted for an internal program, because every request checks the account and a reset or a disabling ends the token on the server |
| Design before code | Each page is drawn in Figma first and built after the owner has seen it | Building with the default look and adjusting later: the owner wants the look decided first |
| The font of the draft | Inter, because the Figma file has no font of the product yet. Ant Design uses the system font of the device, so the built pages show Segoe UI on Windows | Fixing one web font now: it is a detail of the visual design, left open |
| The wording of a lock or a limit | "Please try again in a minute.", for every case, with no time. The owner's decision | The time the lock ends, which the API sends as `lockedUntil` (the first draft). "Please try again later.", which is true in every case. The chosen sentence is not true when the lock is longer than a minute or when a limit on code requests lasts to the end of the hour or the day; the owner was told |
| Where a value is checked | In the page before it is sent, and again in the server | Only in the server: every slip would cost a request, and a wrong code format would be counted as a wrong entry of the code |
| A failure of the server | Two messages, one with the request id to report and one for no answer | One general message: a person could not tell a broken connection from a fault of the program |
| A second password field at sign-up | Not in the draft: the field has a Show control instead | "Confirm password": one more field, and the backend takes one password. The owner can ask for it |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design document](../frontend/page-design.md) | Updated: the Purpose, the new section Frontend Stack And Design Files, the account pages, Open Details, and the Change Log |
| [UI design file in Figma](https://www.figma.com/design/68brMszRAYI4d261GKL1GU) | New: the page "Login and Sign-up" with the components and the 14 frames. It is not tracked by git |
| [Backend design document](../backend/backend-design.md) | Updated: one sentence in Authentication, where the token is kept (a note, no Change Log entry) |
| [README.md](../../README.md) | Updated: Design Documents and the Project Log |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated: T-18 is done and removed, T-60 and T-61 are new, and Phase 7 says that the frontend is built beside the backend |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: the follow-up task on the framework is ticked |
| Database design, API design | Not changed: the pages use the endpoints and the result codes that exist |

## Open Items

- Done in [frontend-v1.3.md](frontend-v1.3.md): the owner reviewed the draft, and the React project and the four pages are built.
- The rest of the visual design and the open details of the other pages (Q-34).
