# API Iteration v1.10

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API design and understand how it evolved.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.10 |
| Date | 2026-11-18 |
| Baseline | [api-v1.9.md](api-v1.9.md) |
| Type | Wider meanings of two result codes at login. No path, field, or format changed |
| Trigger | The login lockout counts by client address (Q-07, [database-v1.18.md](database-v1.18.md)) |

## Summary

- `LOGIN_LOCKED` also means that this client address is locked out of the account. The body is the same, with `lockedUntil`, and does not say which lock it is.
- `TOO_MANY_REQUESTS` also refuses a login from a client address with 50 failures in 15 minutes, on any username. It comes before the username is read, so it says nothing about the username.
- `POST /auth/password-reset` clears the locks of failed logins at every address.

## Changes

| Section | Change |
| --- | --- |
| Result codes | `LOGIN_LOCKED` and `TOO_MANY_REQUESTS` |
| Rules for choosing a status | The login answers for a locked login and for an address with too many failures |
| Authentication | The refusals of the login, and what a reset clears |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| The code for an address with too many failures | `TOO_MANY_REQUESTS`, the code of the other limits of a client address | `LOGIN_LOCKED`: it would carry a `lockedUntil` for an account the address may not even have named |
| Whether `LOGIN_LOCKED` says which lock | No: the page shows the same message, and the answer does not tell a stranger that the account is locked for everybody | A field with the kind of lock |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design](../api/api-design.md) | Updated: the sections above and the Change Log |
| Backend | Built in [backend-v1.12.md](backend-v1.12.md) |
| Frontend | [frontend-v1.10.md](frontend-v1.10.md): the login page already shows `TOO_MANY_REQUESTS` as "Too many requests"; a test covers it |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
