# Backend Iteration v1.4

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.4 |
| Date | 2026-11-12 |
| Baseline | [backend-v1.3.md](backend-v1.3.md) |
| Type | Implementation (the request id and continuous integration) and rules for the modules that follow |
| Trigger | A review of the plan from an architect's point of view, before Phase 2 of the [roadmap](../implementation-roadmap.md) starts. The owner chose the points that must be done and the ones that cost little |

## Summary

- Every request has an id, in the response header, in every error body, and on every log line.
- GitHub Actions runs the whole test suite on every push.
- Three rules are written down before the first business module: no slow work inside a transaction, the rules for scheduled jobs, and which rules a test must check.
- No migration was added, and nothing in the database changed. The suite passes: 85 tests.

## Changes

| Area | Change |
| --- | --- |
| Request id | `RequestIdFilter` in `common.web` makes an id of 32 hexadecimal characters for every request, returns it in `X-Request-Id`, and puts it into the logging context. `logging.pattern.correlation` in `application.yml` prints it on every log line. `ApiExceptionHandler` adds it to every error body as `requestId` (see [api-v1.2.md](api-v1.2.md)) |
| Continuous integration | `.github/workflows/backend-tests.yml` runs `mvnw test` on a clean Linux machine on every push to any branch. It needs no secret and no environment variable |
| Transactions | New rule, No slow work inside a transaction: checking or hashing a password, sending an email, and writing a file happen outside it, and a `REQUIRES_NEW` method is called only after the outer transaction has ended |
| Scheduled jobs | New section: the application is designed for one instance, a job is safe to run twice, and a run handles a limited number of rows |
| Rules checked by tests | New section: a rule that every module must keep is a test that fails the build. It lists the two tests that exist and the four that are due with their module |
| Tests | Five new tests in `ApiConventionsTest` for the request id. The test log and the Tests section of the backend design are updated |
| To-do list | T-20, T-24, T-27, T-29, T-33, and T-49 name the rule above that applies to them. T-54 is new: the test of the package dependencies. T-55 is new: read the first run of the job. Q-32 keeps the deployment plan, and gains the counters, the health check, and the question of more than one instance |
| Roadmap | T-54 is in Phase 3, and Phase 8 no longer lists continuous integration |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Whether the tests run in continuous integration (part of Q-32) | Yes, on GitHub Actions, on every push to any branch | Running them only by hand, which depends on remembering to, and on what happens to be installed on one machine |
| When the job runs | On a push, and by hand from the GitHub page | On a pull request as well: a pull request from a branch of this repository would run the same commit twice |
| The verification script in the job | Not run there. It needs the local MySQL service and the root password | Moving it to the job now, which needs a MySQL service in the job and a change to how the script gets its accounts |
| Where the request id of a log line comes from | The logging context of the thread, set by a filter that runs before every other filter | Passing the id to every method that logs |
| An id sent by the client | Ignored | Accepted, which lets a client write its own text into the log |
| Where a password is checked at login | Before the transaction that records the result | Inside it, under a lock on the user row: ten logins at the same moment would then hold all ten connections of the pool for the time of the hashing |
| How the rule on slow work is kept | The components that hash a password and send an email refuse to run inside a transaction, as `TransactionRetry` does | A sentence in the document only |
| Several instances of the application | Not supported for now, and stated as an assumption | A distributed lock for the jobs, which nothing needs until there is a deployment plan (Q-32) |
| How the permission rule is kept (T-27) | Security refuses whatever no rule allows, and a test fails for an endpoint with no declared permission | A check written in each controller, which a new endpoint can forget without any error |
| How the ticket status rule is kept (T-33) | One table of allowed transitions in the code, and a test over every pair of statuses | A separate check in each method that changes the status |
| When the test of the package dependencies is written (T-54) | When the second module exists | Now: with only the `common` package there is nothing for it to check |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Layout, Shared Code, Request id and logs, Transactions, Scheduled Jobs, Rules Checked By Tests, Tests, Continuous integration, Configuration, and the Change Log |
| [API design document](../api/api-design.md) | Updated: the request id (see [api-v1.2.md](api-v1.2.md)) |
| [concurrency-tests.md](../../db/verification/concurrency-tests.md) | Updated: `ApiConventionsTest` has 20 tests, the Shared code block has 78, and the latest run is 85 passed, 0 failed |
| [README.md](../../README.md) | Updated: Project Log |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated |
| Database design, migrations, ERDs | Not changed. The rule that a transaction makes no call outside the database was already in Concurrency by Module |
| `db/verification/run-db-verification.ps1` | Not run, because no schema, privilege, or flow changed |
| [environment-setup.md](../environment-setup.md) | Not changed: the application reads no new environment variable |

## Verification

- `mvnw test` on 2026-11-12, with the four database environment variables unset as on the machine of the job: 85 passed, 0 failed.
- The job itself has not run yet. It runs for the first time when this change is pushed, and its first result has to be read on GitHub.

## Open Items

- Read the result of the first run of the job on GitHub after the push. A test that passes on Windows and fails on Linux would show there (T-55).
- The `401` and `403` answers of the security layer must carry `requestId` (T-20).
- The components that check a password and send an email, and their refusal to run inside a transaction, are built with authentication (T-20, T-29).
- The limit of rows for each run of a job is set when the job is built (T-49, T-33).
- The test of the package dependencies waits for the second module (T-54).
- Counters for retries and `BUSY` answers, and a health check, were put off until there is a deployment plan (Q-32).
