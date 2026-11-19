# API Iteration v1.13

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API design and understand how it evolved.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.13 |
| Date | 2026-11-19 |
| Baseline | [api-v1.12.md](api-v1.12.md) |
| Type | Two new paths: the description of the API and its page. No endpoint, result code, or format of the API changed |
| Trigger | The owner, 2026-11-19, preparing the project for a résumé: a reader should see every endpoint and who may call it without reading the code |

## Summary

- `GET /api/v1/api-docs`: the OpenAPI description of the API.
- `/api/v1/docs`: Swagger UI on that description.
- Both open with no login. Each operation says who may call it, from the declaration the check reads, and every operation that is not public asks for the bearer token.

## Changes

| Section | Change |
| --- | --- |
| Change Log | The row of 1.13 |
| API Documentation | New section |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the page is | Under the base path, `/api/v1/docs`, so nginx and the development server pass it on as they pass the API | At the root of the backend, which the reverse proxy does not reach |
| Who may open it | Anyone, with no login: it describes what the repository already shows, and every call from it still needs a token | Only a logged-in user, which would hide the documentation from a reader of the project |
| What it says about permissions | Written from the declaration of each method, the same one the check reads | A text written by hand on each method, which could disagree with the check |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design](../api/api-design.md) | Updated |
| [Backend design](../backend/backend-design.md) | Updated in [backend-v1.18.md](backend-v1.18.md) |

## Open Items

None.
