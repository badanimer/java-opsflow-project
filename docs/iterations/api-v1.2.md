# API Iteration v1.2

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.2 |
| Date | 2026-11-12 |
| Baseline | [api-v1.1.md](api-v1.1.md) |
| Type | Addition to the conventions, made before the first module adds an endpoint |
| Trigger | A review of the plan found that nothing connected an error that a user reports with the log lines of that request. The filter is built in [backend-v1.4.md](backend-v1.4.md) |

## Summary

- Every response carries a request id in the header `X-Request-Id`.
- Every error body carries the same value as `requestId`.

## Changes

| Area | Change |
| --- | --- |
| General rules | New row, Request id: the header `X-Request-Id` on every response, 32 hexadecimal characters, new for each request. An `X-Request-Id` sent by the client is ignored |
| Error response | New field `requestId` in every Problem Details body, equal to the header of the same response |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Who makes the id | The server, for every request | Taking the id from a header of the client: a client could then write any text into the log, or reuse an id so that two requests cannot be told apart |
| Where the client finds it | In a header on every response, and also in the body of an error | The header alone: a screenshot of an error message shows the body and not the headers |
| The form | 32 hexadecimal characters (a UUID without hyphens), like the `ord-` numbers | A counter, which would start again at each restart and repeat |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated: General Rules, Error Response, and the Change Log |
| [README.md](../../README.md) | Updated: Project Log |
| Result codes | Not changed |
| Database design, migrations, ERDs | Not changed. The id is not stored in any table |

## Open Items

- The `401` and `403` answers of the security layer must carry `requestId` too. They are written when authentication is built (T-20).
