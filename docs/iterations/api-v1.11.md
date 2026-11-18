# API Iteration v1.11

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API design and understand how it evolved.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.11 |
| Date | 2026-11-18 |
| Baseline | [api-v1.10.md](api-v1.10.md) |
| Type | A filter and a read rule on one endpoint. No path, result code, or status code is new |
| Trigger | Q-34, decided by the owner on 2026-11-18 ([database-v1.19.md](database-v1.19.md)) |

## Summary

- `GET /order-records` takes `userId`, the person who ordered, for a caller with the new permission `ORDER_VIEW_ALL` (`ADMIN`, `MANAGER`, `VIEWER`).
- Without `ORDER_VIEW_ALL` the endpoint returns only the caller's own attempts, in every form of the request, and a `userId` is `FORBIDDEN`.
- `userId` with `orderNo` is `VALIDATION_FAILED` on `orderNo`, because the order number is used alone.
- `GET /user-options?use=filter` is open to `ORDER_VIEW_ALL` too.

## Changes

| Section | Change |
| --- | --- |
| Change Log | The row of 1.11 |
| Logs | The row of `GET /order-records` with `userId` and the rule of one's own records; One order number lists `userId`; the new note Who sees which order records |
| Tickets | `GET /user-options`: `use=filter` also for `ORDER_VIEW_ALL` |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| An operator who sends `userId` | `FORBIDDEN`, as a ticket search without `TICKET_SEARCH` | Ignoring the parameter, which would hide a mistake of the page |
| An operator's own records | Narrowed by the server in every request | A parameter the page sends, which a request could leave out |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design](../api/api-design.md) | Updated: Change Log, Logs, the user picker |
| [Backend design](../backend/backend-design.md) | Updated in [backend-v1.14.md](backend-v1.14.md): the endpoint is built |
| [Page design](../frontend/page-design.md) | Updated in [frontend-v1.12.md](frontend-v1.12.md) |

## Open Items

None.
