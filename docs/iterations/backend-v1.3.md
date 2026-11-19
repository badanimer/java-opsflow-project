# Backend Iteration v1.3

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.3 |
| Date | 2026-11-09 |
| Baseline | [backend-v1.2.md](backend-v1.2.md) |
| Type | Implementation: Phase 1 of the [roadmap](../implementation-roadmap.md), the foundations that every module uses |
| Trigger | The decisions and the API conventions were made, and no controller can be written before the error handler, the paging, and the retry component exist (T-52) |

## Summary

- The `common` package now holds the foundations: the error handler and the result codes, paging by key, the retry component, UTC time helpers, the base path, the idempotency key check, the length limits, and the operating parameters.
- Nothing in it needs a database table of its own, and no migration was added.
- 73 new tests cover it, among them two that make MySQL produce a real deadlock and a real lock wait timeout. The whole suite passes: 80 tests.

## Changes

| Area | Change |
| --- | --- |
| Error handling (T-52) | `ErrorCode` lists every result code of the API with its status and title. `ApiException` carries a code, a detail, and extra fields. `ApiExceptionHandler` makes the Problem Details body for it, for a failed validation, and for the errors Spring MVC raises itself, and answers an unexpected error with `INTERNAL_ERROR` and no detail |
| Paging (T-52) | `Cursor`, `CursorCodec`, `PageQuery`, `PageQueryFactory`, and `PageResponse`. A bad parameter is refused, not corrected |
| Retry (T-52) | `TransactionRetry` retries a call on a deadlock or a lock wait timeout, outside the transaction, and answers `BUSY` when the attempts are used up |
| Base path and idempotency (T-52) | `WebConfig` serves every `@RestController` under `/api/v1`. `IdempotencyKey.parse` checks the header |
| Time (T-09) | A `Clock` bean in UTC, `UtcTime` for the conversions, and a test that fails when code reads the default time zone |
| Length limits (T-13) | `Limits` holds one constant for each text field, and a test compares each constant with its column |
| Operating parameters (T-11) | `OpsflowProperties` binds `opsflow.retry.*` and `opsflow.paging.*` from `application.yml` |
| Tests | Eight test classes and a test controller under `src/test/java/com/opsflow/common`, listed in the Tests section of the backend design and in the test log |
| To-do list | T-52 and T-09 removed. T-11 and T-13 keep the part that depends on later modules. T-45 notes that the codec and the response type exist |
| Roadmap | Phase 1 is marked done |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| A paging parameter out of range | Refuse with `VALIDATION_FAILED` | Correct it silently, for example a `limit` of 50 cut to 10, which hides a client mistake |
| The cursor | URL-safe Base64 of `<id>:<key>`, not signed | A signed cursor: it carries no right, and the key is always passed to the database as a parameter |
| What counts as a lock conflict to retry | `PessimisticLockingFailureException` and a raw `SQLException` with error 1213, 1205, or SQL state 40001, found anywhere in the cause chain; never when an `ApiException` is in the chain | Retrying every `DataAccessException`, which would repeat a duplicate-key failure for nothing |
| Calling the retry inside a transaction | Refused with `IllegalStateException` | Allowing it, which would retry on a connection that is already rolled back |
| The wait before a retry | The configured delay plus a random extra of up to half of it | A fixed delay, which lets two requests that collided collide again |
| The key of an idempotency header | 1 to 128 visible ASCII characters | Any text, which the 128-character column could not hold exactly |
| How the base path is applied | One rule in `WebConfig` for every `@RestController` in `com.opsflow` | A prefix written on each controller, which a new controller could forget |
| Where the limits live | Constants in `Limits`, compared with the database by a test | Reading the lengths from the database at runtime, which annotations cannot use |
| Two new result codes | `METHOD_NOT_ALLOWED` (405) and `UNSUPPORTED_MEDIA_TYPE` (415), because Spring raises these itself | Reporting them as `VALIDATION_FAILED` with a status that does not fit |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Shared Code, Transactions, Tests, Configuration, and the Change Log |
| [API design document](../api/api-design.md) | Updated: the key format, the paging refusals, and two result codes (see [api-v1.1.md](api-v1.1.md)) |
| [concurrency-tests.md](../../db/verification/concurrency-tests.md) | Updated: the Shared code block, row D6, and the latest run (80 passed, 0 failed) |
| [README.md](../../README.md) | Updated: Project Log |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated |
| Database design, migrations, ERDs | Not changed |
| `db/verification/run-db-verification.ps1` | Not run, because no schema, privilege, or flow changed |
| [environment-setup.md](../environment-setup.md) | Not changed: the application reads no new environment variable |

## Open Items

- The `TEXT` columns (`tickets.description` and `tickets.resolution`) have no length limit yet. It is set when the ticket module is built (T-13).
- `TransactionRetry` is used by no service yet. The order module is its first caller (T-10).
- The `401` and `403` answers of the security layer are not part of the error handler yet. They are added when authentication is built (T-20).
