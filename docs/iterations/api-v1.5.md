# API Iteration v1.5

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.5 |
| Date | 2026-11-15 |
| Baseline | [api-v1.4.md](api-v1.4.md) |
| Type | The first five endpoints of the user module, the first list among them, and three new result codes |
| Trigger | The four changes one user makes to another are built ([backend-v1.7.md](backend-v1.7.md)), following the steps confirmed in [database-v1.12.md](database-v1.12.md) |

## Summary

- `GET /users` is the user page and the first list of the API: paged by key, ordered by username from A to Z, with `search` and `roles`.
- `PUT /users/{id}/roles` sets the roles of a user to a list. The request also carries the roles the sender saw.
- `POST /users/{id}/disable` and `POST /users/{id}/enable` change the status of a user, and `DELETE /users/{id}` deletes softly.
- Three new codes, all `409`: `USER_CHANGED`, `USER_NOT_ACTIVE`, and `LAST_ADMIN`.
- `GET /auth/me` can return two more permission names, `ROLE_ASSIGN_PROTECTED` and `USER_MANAGE_PROTECTED`.

## Changes

| Area | Change |
| --- | --- |
| Users | `GET /users` with the paging parameters and `search` and `roles` in, and a page of `{ "id", "username", "status", "roles" }` out |
| Paging | `order` says that a list whose order is fixed reads in its own order, which for the user page is from A to Z and not `desc` |
| Users | New section: `PUT /users/{id}/roles` with `{ "roles", "expectedRoles" }` in and `200` with `{ "id", "username", "roles" }` out |
| Users | `POST /users/{id}/disable` and `POST /users/{id}/enable` with no body in and `200` with `{ "id", "username", "status" }` out, and `DELETE /users/{id}` with `204` |
| Result codes | New: `USER_CHANGED` (409), which carries `userStatus` when a status change found the other status, `USER_NOT_ACTIVE` (409), `LAST_ADMIN` (409) |
| Result codes | `FORBIDDEN` and `NOT_FOUND` name their use in a change to a user |
| Rules for choosing a status | One rule added: which answer a change to a user gets |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How the role buttons are sent | One parameter `roles` with the codes, and `NONE` for No role | A parameter for each role: a new role would need a new parameter. A separate `noRole=true`: two parameters for one row of buttons |
| An unknown role code in `roles` of the list | `VALIDATION_FAILED` | Ignoring it: a page that is out of date would show a list narrowed by nothing and not know it |
| The fields of a row of the user page | `id`, `username`, `status`, and `roles` | Also the email: the page does not show it, and a manager has no need to read the addresses. Also a flag for whether the row may be changed: the frontend knows its own id and its permissions, and the server checks every change again |
| The order of the user page | Fixed, by username from A to Z | The default `desc` of the other lists: a list of names is read from A |
| The shape of the operation | `PUT` of the whole list of roles on the sub-resource `roles` of a user | One call for each grant and each revocation: the user page changes several roles at once, and a change made of several calls could stop halfway |
| How the server knows what the sender saw | `expectedRoles` in the body | A version number or an `If-Match` header: `users` has no version column, and the roles are short enough to send back |
| Roles that differ from `expectedRoles` | `409` with `USER_CHANGED`, and nothing is changed | Applying the new list anyway: a role that someone else just gave would be taken away without anyone having seen it |
| A change to one's own roles, a manager who changes a manager, a manager who gives `MANAGER` | `403` with `FORBIDDEN` and a detail for the person | A code for each: the frontend does the same in each case, it shows the message. The user page offers none of these controls, so they are reached only by a request made by hand or by a page that is out of date |
| A target that does not exist, is soft-deleted, or is the SYSTEM identity | `404` with `NOT_FOUND`, the same for the three | `403`: it would say that the id belongs to something |
| A manager who names a user who holds `ADMIN` or `MANAGER` | `403` with `FORBIDDEN` | `404`: a manager knows that the other managers exist, from the ticket screens |
| Adding a role to a disabled user | `409` with `USER_NOT_ACTIVE` | `VALIDATION_FAILED`: the request is well formed, and it works once the user is enabled |
| An actor whose account changed after the request arrived | The answers of the token check: `UNAUTHENTICATED`, `ACCOUNT_DISABLED`, or `ACCOUNT_NO_ROLE` | `FORBIDDEN` for all: the frontend would not know to clear the login state or to open the pending page |
| The shape of a status change | `POST` on a sub-resource named after the action, as the General Rules say: `disable` and `enable` | `PATCH /users/{id}` with a `status` field: it would look as if other fields of a user could be changed the same way, and none can |
| The shape of a soft deletion | `DELETE /users/{id}` with `204` | `POST /users/{id}/delete`: for a caller the user is gone afterwards, which is what `DELETE` says. That the row stays is a matter of the database |
| A disabling of a disabled user, an enabling of an active one | `409` with `USER_CHANGED` and the current status in `userStatus` | `200`: the person would not learn that someone else had already acted. A field named `status`: that name is the HTTP status in every error body |
| A change to a deleted user | `404` with `NOT_FOUND` | `409`: nothing the sender does can make the change work |
| An unknown role code in `roles` | `400` with `VALIDATION_FAILED` on the field `roles` | `NOT_FOUND` |
| An unknown role code in `expectedRoles` | `USER_CHANGED`: the user does not hold it, so the lists differ | A validation error: the list says what the sender saw, and is only compared |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: Result Codes And Status Codes, the new section Users, and the Change Log |
| `ErrorCode.java` | Updated: `USER_CHANGED`, `USER_NOT_ACTIVE`, and `LAST_ADMIN` |
| Backend | The endpoint and its answers are built and tested in [backend-v1.7.md](backend-v1.7.md) |
| Database design | The table of refusals in Steps of a change to a user, in [database-v1.13.md](database-v1.13.md) |
| [page-design.md](../frontend/page-design.md) | Not changed. What the user page shows for each code is written when the page is drawn (Q-34) |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- What the user page shows for `USER_CHANGED`, `USER_NOT_ACTIVE`, and `LAST_ADMIN` (Q-34).
