# Frontend Iteration v1.1

## Purpose

This file records one iteration of the OpsFlow page design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Page Design](../frontend/page-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.1 |
| Date | 2026-11-12, extended on 2026-11-13 when sign-up and password recovery were built |
| Baseline | [frontend-v1.0.md](frontend-v1.0.md) |
| Type | A change to two pages, the login page and the pending page, and details of two more, the sign-up page and Forgot password |
| Trigger | The owner's decision on how a disabled account is handled, recorded in [database-v1.11.md](database-v1.11.md) |

## Summary

- A disabled account is told so on the login page and gets no token, so it never reaches the pending page.
- A user whose account is disabled while logged in is sent back to the login page with the same message.
- The pending page is only for a user with no role.
- The sign-up page and Forgot password say what they show for each answer about an email code, and the username rule is stated.

## Changes

| Page | Before (v1.0) | Now |
| --- | --- | --- |
| Login | Nothing about a disabled account | On `ACCOUNT_DISABLED` the page stays and shows "This account has been disabled. Please contact an administrator." |
| Every page | Nothing about an account disabled after login | On `ACCOUNT_DISABLED` from any request, the frontend clears its login state, goes back to the login page, and shows the same message. After the account is enabled again, the user logs in again |
| Pending page | Two messages: the account is disabled, or it has no role | One message: "Waiting for an administrator to assign permissions" |
| Page Map | The pending page is seen by a user with no role or a disabled account | By a user with no role |
| Sign-up, Forgot password | A code is sent, and a new one can be requested after 60 seconds | The button for a new code is disabled for 60 seconds. Forgot password always says that a code was sent if the address has an account. Messages for `TOO_MANY_REQUESTS`, `CODE_INVALID`, `USERNAME_TAKEN` (the code is kept, another username is asked for), `EMAIL_TAKEN`, and a new password equal to the current one |
| Sign-up | A username, an email, and a password. After it the user logged in | The same, with the username rule: 3 to 64 letters and digits, with no space and no other character. The sign-up logs the user in, and the page goes straight to the pending page. A request for a code for a registered address shows "This email is already registered." |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where a disabled account is told | On the login page | On the pending page after a successful login (v1.0) |
| What happens when an account is disabled during a session | Back to the login page, with the login state cleared | Staying on the current page with an error: every further request would fail the same way |
| What Forgot password says after a request for a code | Always the same sentence, that a code was sent if the address has an account | "This address is not registered": the server does not say it, on purpose |
| What the sign-up page says for a registered address | "This email is already registered.", with links to the login page and to Forgot password. The owner's decision on 2026-11-13 | The same sentence as for a free address: the person would wait for a mail that never comes |
| Where the user lands after sign-up | On the pending page, logged in. The owner's decision on 2026-11-13 | On the login page, to type the same username and password again |
| After `USERNAME_TAKEN` | Keep the code and ask for another username | Start again with a new code: the code is still valid, and a new one cannot be requested for 60 seconds |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Page design document](../frontend/page-design.md) | Updated: Page Map, the login page, the pending page, the sign-up page and Forgot password, and the Change Log |
| [API design document](../api/api-design.md) | Updated in [api-v1.3.md](api-v1.3.md): the endpoints and the codes these pages use |
| [Database design document](../database/database-design.md) | Updated in [database-v1.11.md](database-v1.11.md): Users without roles and Login security and account recovery. Screens and Lists and the Permission Matrix are not changed |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- Build the pending page and the handling of `ACCOUNT_DISABLED` (T-22), when the frontend starts.
- Build the sign-up page and Forgot password (T-58), when the frontend starts.
