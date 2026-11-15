# Database Iteration v1.14

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Database Design](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.14 |
| Date | 2026-11-15 |
| Baseline | [database-v1.13.md](database-v1.13.md) |
| Type | One new nullable column on `users`, and the rules of login and of the token check that use it. Also the time of a delisting, taken after the lock, found by the verification run of this iteration |
| Trigger | The owner's decision on 2026-11-15: an account is signed in on one device at a time, the newest login wins, and the earlier device is told at once |

## Summary

- `users.session_id` holds the id of the newest login of the account: 16 random bytes in hexadecimal.
- A successful login, and the sign-up that logs the new user in, write a new id in the transaction that holds the user row. The token carries it.
- The token check refuses a token whose id is not the current one with `SESSION_REPLACED`, so every earlier login ends at its next request.
- An open page of the earlier login is told at once through an event stream; the token check is what enforces the rule.
- A delisting locks the product row before it takes the time of `delisted_at`, so no order that committed first looks later (T-66).

## Changes

| Area | Before (v1.13) | Now |
| --- | --- | --- |
| `users` | No record of the login a token belongs to | `session_id CHAR(32) NULL`, added by `V4__users_session_id.sql` after `last_login_at` |
| Login steps | A success cleared the count and the lock and set `last_login_at` | It also writes a new `session_id`, and the token carries it |
| Sign-up | The new user was inserted with `last_login_at` | Also with a new `session_id`, which the first token carries |
| Token checks | Refused a token not issued after `password_changed_at` | Also refuses a token with no login id (`UNAUTHENTICATED`), and one whose id is not `session_id` (`SESSION_REPLACED`). A disabled account is told `ACCOUNT_DISABLED` first |
| One signed-in device | Not designed | A new paragraph under Login security and account recovery: the rule, the event stream, the memory of one server, and the 15-minute stream |
| Concurrency by Module | — | Two rows under Authentication: two logins of one account at the same moment, and a login that commits while the stream of an earlier login is opened |
| Field Specifications | — | `users.session_id` |
| Listing and delisting | The time of a delisting was not said | It is taken after the product row is locked; the same holds for any time written by a statement that may wait for a lock |
| `delist_product` in `concurrency-fixtures.sql` | Set `delisted_at = NOW(3)` in the `UPDATE` that waited for the orders | Locks the row with `SELECT ... FOR UPDATE` first, refuses a missing or unlisted product there, then updates under the lock |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| When two devices log in | The newest wins: the earlier device is signed out | The first one blocks the second: a person who forgot to sign out at work could not sign in at home for 12 hours, and a thief who signed in first would lock the owner out |
| How a token is tied to its login | A random id in `users.session_id`, carried by the token and compared on every request | The issue time compared with `last_login_at`: two logins in the same millisecond could not be told apart, and the issue time is already moved by a password reset. A table of sessions: a row per login for a rule that needs only the newest one |
| Where the rule is enforced | In the token check, which already reads the user row on every request, so it costs no extra query | Only in the event stream: a page whose stream was down would stay signed in |
| How the earlier device learns it at once | A stream of Server-Sent Events that every signed-in page keeps open | WebSocket: two-way, for a message that only goes one way. Asking every minute: up to a minute late, and a request per minute per open tab |
| Which refusal comes first for a disabled account with an ended token | `ACCOUNT_DISABLED` | `SESSION_REPLACED`: the person would sign in again and only then learn the account is disabled |
| Tokens issued before this change | Refused as `UNAUTHENTICATED`: every user logs in once again | Accepted without an id: the rule would not hold until they expire |
| When the time of a delisting is taken | After the lock on the product row is held | In the statement that waits for the lock: MySQL fixes `NOW()` when the statement starts, so the delisting could be dated before orders that committed first. The deletion of an item was checked and already locks first |
| Whether a password reset or Sign out changes `session_id` | No. A reset already ends every earlier token through `password_changed_at`, and Sign out only forgets the token in the browser | A server-side sign-out endpoint: not needed for the rule |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: Login steps, Sign-up, Disabled accounts, Token checks, One signed-in device (new), Concurrency by Module, Field Specifications, and the Change Log |
| Migration | [`V4__users_session_id.sql`](../../backend/src/main/resources/db/migration/V4__users_session_id.sql). The applied migrations are not changed |
| Physical ERD | Updated by the assistant on 2026-11-15 through the Figma tools: the row `char | session_id` after `last_login_at` in the table `users` |
| Simplified ERD and Business Process Flowchart | Not changed: no table or relationship changed |
| [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) | Not changed: the application account already has `UPDATE` on `users` |
| [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1) | Not changed. Run twice on 2026-11-15. After the new column: 160 passed, 1 failed, `five orders racing a delisting`, because `delist_product` took `delisted_at` before it held the lock. After the fix of the procedure: 161 passed, 0 failed. See the [test log](../../db/verification/concurrency-tests.md#latest-run) |
| [`concurrency-fixtures.sql`](../../db/verification/concurrency-fixtures.sql) | `delist_product` locks before it takes the time |
| Backend | [backend-v1.8.md](backend-v1.8.md) |
| API | [api-v1.6.md](api-v1.6.md): the code `SESSION_REPLACED` and `GET /auth/session-events` |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- With a second server, the event of a login must reach the streams on the other servers, for example through Redis publish and subscribe (recorded in the design; no task until a second server is planned).
