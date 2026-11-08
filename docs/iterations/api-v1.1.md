# API Iteration v1.1

## Purpose

This file records one iteration of the OpsFlow API: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current API conventions and understand how they were decided.

The authoritative description is [OpsFlow API](../api/api-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.1 |
| Date | 2026-11-09 |
| Baseline | [api-v1.0.md](api-v1.0.md) |
| Type | Clarification found while writing the conventions into code (T-52) |
| Trigger | The error handler, the paging, and the idempotency check were implemented in [backend-v1.3.md](backend-v1.3.md), and three points were not settled by v1.0 |

## Summary

- The idempotency key has a defined format.
- A paging parameter that is out of range is refused, not corrected, and the cases are listed.
- Two result codes were added, for errors that Spring MVC raises by itself.

## Changes

| Area | Change |
| --- | --- |
| General rules | The `Idempotency-Key` is 1 to 128 visible ASCII characters, because it is stored in a 128-character column. A bad key is refused with `VALIDATION_FAILED` |
| Paging | The cases that are refused with `VALIDATION_FAILED`: a `limit` below 1 or above the page size, an unknown `direction` or `order`, `prev` without a cursor, an `order` on a list whose order is fixed, and a cursor that does not decode. Values are read without regard to case |
| Result codes | New: `METHOD_NOT_ALLOWED` (405) and `UNSUPPORTED_MEDIA_TYPE` (415) |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| A paging parameter out of range | Refuse | Cut it to the allowed value, which hides a client mistake |
| A wrong method or media type | Their own codes and statuses | `VALIDATION_FAILED` with a 405 or 415 status, which would break the rule that a code names one result |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [API design document](../api/api-design.md) | Updated |
| [README.md](../../README.md) | Updated: Project Log |
| Database design, migrations, ERDs | Not changed. The two new codes belong to no database module |

## Open Items

- The 401 and 403 answers of the security layer are written when authentication is built (T-20), and use the codes already in the table.
