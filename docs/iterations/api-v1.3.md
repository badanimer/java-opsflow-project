# API Iteration v1.3

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.3 |
| Date | 2026-11-12, extended on 2026-11-13 when login was built, and again the same day when sign-up and password recovery were built |
| Baseline | [api-v1.2.md](api-v1.2.md) |
| Type | The meaning of two existing codes, fixed before authentication is built, the first five endpoints, and three new codes |
| Trigger | The owner's decisions on the login flow, recorded in [database-v1.11.md](database-v1.11.md) |

## Summary

- `ACCOUNT_DISABLED` (403) is the answer of the login endpoint to a disabled account whose password is right, with no token, and the answer of every other endpoint once an account is disabled after login.
- `UNAUTHENTICATED` (401) also covers a soft-deleted account, at login and in the token check, and a login whose password was replaced by a reset while it was being checked.
- The API has its first endpoints: `POST /auth/login` and `GET /auth/me`.
- Three more endpoints, none of which needs a token: `POST /auth/email-codes`, `POST /auth/sign-up`, and `POST /auth/password-reset`.
- Three new codes: `CODE_INVALID` (400), `USERNAME_TAKEN` (409), and `EMAIL_TAKEN` (409). `TOO_MANY_REQUESTS` (429) now covers the three limits on code requests.
- A request for a sign-up code for an address that has an account gets `EMAIL_TAKEN`. A request for a recovery code gets the same `204` whether or not a mail is sent.
- A sign-up returns a login token with the new account.

## Changes

| Area | Change |
| --- | --- |
| Result codes | `UNAUTHENTICATED`: the meaning now names a wrong username or password at login and a token of a user who does not exist or is soft-deleted |
| Result codes | `ACCOUNT_DISABLED`: the meaning now covers the login and the later requests, and says what the frontend does. It no longer refers to the pending page |
| Authentication | New section: `POST /auth/login` (username and password in, `token` and `expiresAt` out) and `GET /auth/me` (`id`, `username`, and `roles`, empty for a user who waits for a role) |
| General rules | The Authentication row says where the token comes from and how long it lasts |
| Result codes | `LOGIN_LOCKED` carries `lockedUntil`. `FORBIDDEN` is also the answer to a request that no rule allows. `UNAUTHENTICATED` says "not issued after `password_changed_at`" |
| Rules for choosing a status | Three rules added: a disabled account at login, a login that raced a password reset, and what the frontend does with `ACCOUNT_DISABLED`, `UNAUTHENTICATED`, and `ACCOUNT_NO_ROLE` |
| Authentication | `POST /auth/email-codes` (`email` and `purpose` in, `204` out), `POST /auth/sign-up` (`username`, `email`, `password`, and `code` in, `201` with `id`, `username`, `token`, and `expiresAt` out), and `POST /auth/password-reset` (`email`, `code`, and `newPassword` in, `204` out) |
| Result codes | New: `CODE_INVALID` (400), `USERNAME_TAKEN` (409), `EMAIL_TAKEN` (409) |
| Result codes | `TOO_MANY_REQUESTS`: was "a code was requested again within 60 seconds"; now also more than 10 requests of one client address in an hour and more than 200 of the whole system in a day, with one answer for the three |
| Rules for choosing a status | Two rules added: the same answer for a code request and one code for every problem with a code; and `409` for a taken username or address against `VALIDATION_FAILED` for a value that breaks a rule |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The status of a disabled account at login | `403` with `ACCOUNT_DISABLED`, the code that already exists | `401`: the frontend could not tell it from a wrong password and could not show the message |
| When the login says so | Only when the password is right | For any attempt, which would tell a stranger the state of the account |
| How the frontend learns that a user has no role | `GET /auth/me` returns the roles, and an empty list means the pending page | A separate state field: the roles already say it |
| A request to a path that does not exist | `UNAUTHENTICATED` without a token and `FORBIDDEN` with one, because security refuses whatever no rule allows before the path is looked up | `NOT_FOUND`, which would need a rule that lets unknown paths through and would tell a stranger which paths exist |
| A login that raced a password reset | The same `401` as a wrong password | A code of its own: the person only has to log in again, and a new code would say that a reset has just happened |
| The answer to an accepted request for a code | `204` with no body | `200` with the time the code expires or the time of the next allowed request: for a recovery address that got no mail the body would have to be made up |
| A problem with a code | One code, `CODE_INVALID`, with one text | A code for each reason (wrong, expired, used, too many entries): it would show whether a code exists for an address, and so whether the address is registered |
| The status of `CODE_INVALID` | `400` | `401`: the frontend treats a `401` as "log in again". `409`: a refresh does not fix a wrong code |
| The answer to a sign-up | `201` with the id, the username, and a login token, so the new user is on the pending page at once. The owner's decision on 2026-11-13 | No token and a login as the next step (the first version of this iteration). The worry behind it, a second way in without the checks of the login, does not apply: the token is issued only in the request that creates the account, with a right email code, and never for an account that exists |
| A sign-up code for an address that has an account | `409` with `EMAIL_TAKEN`. The owner's decision on 2026-11-13 | The same `204` as for a free address, which hides which addresses are registered and leaves the person waiting for a mail |
| Which limit refused a code request | Not said: one `TOO_MANY_REQUESTS` | A field that names the limit or the time to wait: it would tell a script exactly how fast it may go |
| The names of the paths | `email-codes` as a resource that is created, and `sign-up` and `password-reset` as actions | `POST /users` for sign-up: the user module will have its own `/users` for the user page, with other rules |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: General Rules, Result Codes And Status Codes, the rules for choosing a status, the new Authentication section with its five endpoints, and the Change Log |
| [README.md](../../README.md) | Updated: Project Log |
| `ErrorCode.java` | Updated: `CODE_INVALID`, `USERNAME_TAKEN`, and `EMAIL_TAKEN` |
| [page-design.md](../frontend/page-design.md) | Updated in [frontend-v1.1.md](frontend-v1.1.md): what the pages show for each code |
| Backend | The endpoints and the answers are built and tested in [backend-v1.5.md](backend-v1.5.md) |
| Database design | Updated in [database-v1.11.md](database-v1.11.md) |

## Open Items

- None in the API. The answers are returned by the endpoints and by the token check, in the Problem Details body with its `requestId`, since [backend-v1.5.md](backend-v1.5.md). The pages that use the three new endpoints are T-58.
