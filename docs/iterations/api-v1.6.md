# API Iteration v1.6

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.6 |
| Date | 2026-11-15 |
| Baseline | [api-v1.5.md](api-v1.5.md) |
| Type | One new result code and one new endpoint |
| Trigger | One signed-in device per account ([database-v1.14.md](database-v1.14.md)) |

## Summary

- `SESSION_REPLACED` (401): the account logged in again after this token was issued.
- `GET /auth/session-events`: a Server-Sent Events stream for a signed-in page, which sends `session-replaced` and closes as soon as a newer login of the account commits.
- Tokens issued before this change carry no login id and get `UNAUTHENTICATED`.

## Changes

| Area | Change |
| --- | --- |
| Result codes | `SESSION_REPLACED`, 401 |
| Result codes | `UNAUTHENTICATED` also covers a token with no login id |
| Authentication | `GET /auth/session-events` for any logged-in user: `text/event-stream`; the event `session-replaced` with data `{}`; a comment every 25 seconds; closed after 15 minutes; `SESSION_REPLACED` instead of a stream for a token that is already ended |
| Authentication | The rule of one signed-in device, and what the frontend does with `SESSION_REPLACED` |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| A code of its own | `SESSION_REPLACED`, so the page can say that the account signed in elsewhere, and a person notices a stranger | `UNAUTHENTICATED`: a timeout and a stranger's login would look the same |
| Its status | 401: the token no longer authenticates | 403: the request is not about permissions |
| How the page reads the stream | `fetch`, with the `Authorization` header like every request | `EventSource`: it cannot send a header, and the token would have to travel in the address, where logs keep it |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: Result Codes And Status Codes, Authentication, and the Change Log |
| Backend | [backend-v1.8.md](backend-v1.8.md) |
| Frontend | Built in [frontend-v1.5.md](frontend-v1.5.md): the stream read with `fetch`, the dialog, and the notice on the login page |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
