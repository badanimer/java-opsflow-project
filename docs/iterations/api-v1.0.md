# API Iteration v1.0

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.0 |
| Date | 2026-11-04 |
| Baseline | None: the first API iteration |
| Type | New area: the API conventions |
| Trigger | The backend skeleton exists, and the owner chose to fix the conventions before the first controller is written (Q-31) |

## Summary

- A new area, the API, with its own current-state document.
- The conventions follow the common practice of REST APIs on Spring: a versioned base path, JSON in camelCase, UTC times, bearer tokens, and no envelope on success.
- Errors use Problem Details (RFC 9457) with one added field, `code`.
- The result codes of the database design were collected in one table, with a status for each, and a few general codes were added.
- Lists are paged by key with an opaque cursor, as the database design requires.

## Changes

| Area | Change |
| --- | --- |
| API document | New: general rules, the error response, the table of result codes and statuses, and paging |
| Sync map | New row for the API area |
| Open question | Q-31 is decided and removed from the to-do list |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Versioning | A path prefix, `/api/v1` | A version in a request header, which is harder to test in a browser |
| Success body | The resource itself, with no envelope | A wrapper such as `{ "code": 0, "data": ... }`, which hides the HTTP status |
| Error body | Problem Details with a `code` field | A format made only for this project |
| Business conflicts | `409` | `422`, and `200` with an error code in the body |
| Account waiting for a role | `403` with `ACCOUNT_DISABLED` or `ACCOUNT_NO_ROLE`, so the frontend can tell it from an ordinary denial (T-22) | A separate status code |
| Paging | An opaque cursor, `limit`, `direction`, and `order` where the user may choose it; no total | Page numbers and offsets, which the database design rules out |
| Idempotency | The `Idempotency-Key` request header | A field in the request body |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | New |
| [documentation-sync-rules.md](../documentation-sync-rules.md) | Updated: an API row in the Sync Map |
| [README.md](../../README.md) | Updated: Documentation Structure and Project Log |
| [project-todo.md](../project-todo.md) | Updated: Q-31 removed, T-52 added |
| Database design, ERDs, migrations | Not changed. The result codes were collected from the design document without changing them |

## Open Items

- Write the conventions into code: the error handler, the cursor, and the base path (T-52).
- The list of endpoints is not written. Each module adds its own endpoints, and its result codes to the table, when it is built.
