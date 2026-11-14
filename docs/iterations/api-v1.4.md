# API Iteration v1.4

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.4 |
| Date | 2026-11-15 |
| Baseline | [api-v1.3.md](api-v1.3.md) |
| Type | One new field in the answer of an existing endpoint. A compatible change: no path, code, or status changed |
| Trigger | The owner's decision on 2026-11-15, after the endpoints were made to name a permission and not a role ([backend-v1.6.md](backend-v1.6.md)): the frontend should not have to change when a role is added |

## Summary

- `GET /auth/me` also returns `permissions`: what the user may do, as the names of the permissions of all the user's roles together.
- The frontend shows or hides a control by its permission, and never by a role. A new role therefore needs no change in the frontend.
- `roles` stays. The pending page still reads it, and it is what a person sees as their roles.

## Changes

| Area | Change |
| --- | --- |
| Authentication | `GET /auth/me` returns `{ "id", "username", "roles", "permissions" }`. `permissions` is a list of permission names, such as `ORDER_PLACE`, in a fixed order, and is empty for a user with no role |
| Authentication | New rule: the frontend decides what to show from `permissions`, and the server checks every request again |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How the frontend knows what a user may do | The server sends the permissions of the user with the account | The frontend works it out from the roles: the list of what each role may do would exist twice, in the backend and in the frontend, a new role would change every page, and a difference between the two lists shows a button that answers `403` |
| Where the list is sent | In `GET /auth/me`, which the frontend already calls after a login, on a reload, and on the pending page | An endpoint of its own: a second call for something that belongs to the account. In the token: the token would then carry permissions that may have been taken away, and the server reads them for each request anyway |
| What a permission is called in the API | The name of the constant in the code, such as `TICKET_ASSIGN` | Lower-case names with dots: a second spelling to keep in step |
| Whether `roles` stays | Yes | Removing it: the pending page reads it, and the user page shows the roles of a user |
| Whether the names are listed in the API document | No. The document names the enum `Permission` in the backend as the list | A copy of the 25 names: it would have to follow every new action |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: Authentication and the Change Log |
| Backend | `AuthController.AccountResponse` and `CurrentUser.permissions()`, recorded in [backend-v1.6.md](backend-v1.6.md). Tested in `TokenCheckTest.theRolesAreReadFromTheDatabaseOnEveryRequest` and `PermissionTest` |
| Frontend | The type `Account` in `frontend/src/api/auth.ts` has the field. No page reads it yet: the pages built so far show no control that depends on a permission. `npm test` on 2026-11-15: 87 passed |
| [project-todo.md](../project-todo.md) | T-27: the frontend part now says that a control is shown by its permission |
| [README.md](../../README.md) | Updated: Project Log |
| Result codes | Not changed |

## Open Items

- Show and hide each control by its permission as the pages are built (T-27).
- The result codes of the refusals of a change to a user are named when the user module is built (T-21, T-23, T-26).
