# Backend Iteration v1.8

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.8 |
| Date | 2026-11-15 |
| Baseline | [backend-v1.7.md](backend-v1.7.md) |
| Type | A rule of authentication, and the first endpoint that streams |
| Trigger | The owner's decision on 2026-11-15: one signed-in device per account, recorded in [database-v1.14.md](database-v1.14.md) |

## Summary

- A login and a sign-up write a new session id in their transaction; the token carries it as the claim `sid`.
- `TokenAuthenticator` refuses a token of an earlier login with `SESSION_REPLACED`, and a token with no session id as `UNAUTHENTICATED`.
- `SessionEvents` keeps the open event streams of the signed-in pages in memory, and after a login commits it tells the streams of the other logins of the account and closes them.
- `GET /auth/session-events` opens a stream for any logged-in user.

## Changes

| Area | Change |
| --- | --- |
| `TokenService` | `issue(userId, issuedAt, sessionId)` puts `sid` in the token; `sessionId(jwt)` reads it |
| `SessionIds` | New: 16 random bytes in hexadecimal |
| `AuthRepository` | `Account` has `sessionId`; `recordSuccess` and `insertUser` write it; `sessionId(id)` reads it without a lock |
| `LoginRecorder`, `AccountWriter` | Write a new session id with the success or the new account, and return it |
| `LoginService` | Calls `SessionEvents.loggedIn` after the transaction committed, then issues the token |
| `TokenAuthenticator` | Refuses a token with no session id (`UNAUTHENTICATED`), a disabled account (`ACCOUNT_DISABLED`), then a token whose id is not the current one (`SessionReplacedException`). The authentication of a request knows its session id |
| `SecurityErrorHandler` | `SessionReplacedException` becomes `SESSION_REPLACED` (401) |
| `ErrorCode` | `SESSION_REPLACED` |
| `SessionEvents` | New: the streams per user and login, the event `session-replaced`, a comment every 25 seconds, a lifetime of 15 minutes, and the second read of the session id after a stream is registered |
| `AuthController` | `GET /auth/session-events`, `@AnyAccount`, `text/event-stream` |
| `SecurityConfig` | The async dispatch of a stream is allowed: the request was decided when the stream opened |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| When the open pages are told | After the login transaction committed, in `LoginService` | Inside the transaction: a page could be signed out by a login that then rolled back |
| Where the streams are kept | In the memory of the server, which is enough for one instance, like the scheduled jobs | Redis publish and subscribe now: a service for a second instance that does not exist. It is the way when one is added |
| How long a stream lives | 15 minutes, then the page opens a new one, which checks the token again | For the life of the token: a disabled account would keep its stream for up to 12 hours |
| A login between the token check of a stream and its registration | The stream reads the session id again after it is registered | A lock: the stream would hold a database lock while it waits |
| The endpoint of the stream | `@AnyAccount`, so the pending page is told too | A permission: a user with no role is signed in as well |

## Tests

| Class | What it shows |
| --- | --- |
| `SessionTest` (new, 9) | The newest login ends the earlier ones; a sign-up's token is ended by a later login; another account is not affected; a disabled account is told it is disabled; the stream of an earlier login gets the event and ends; the stream of the newest login stays open; an ended token cannot open a stream; a stream opened just after a login it missed is told at once; of two logins at the same moment exactly one stays valid |
| `TokenServiceTest` | The token holds `sid` |

`mvnw test` on 2026-11-15: 310 passed, 0 failed.

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: the classes of Authentication, the notes on the token check and on one signed-in device, the tests of `auth`, and the Change Log |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: `SessionTest` and `TokenServiceTest` under Application tests, and the latest `mvnw test` run |
| [Database design](../database/database-design.md) | [database-v1.14.md](database-v1.14.md) |
| [API design](../api/api-design.md) | [api-v1.6.md](api-v1.6.md) |
| Configuration and environment | Not changed: no new variable |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- A proxy in front of the application must not buffer `text/event-stream`; to be set when the application is deployed.
