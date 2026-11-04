# Backend Iteration v1.2

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.2 |
| Date | 2026-11-05 |
| Baseline | [backend-v1.1.md](backend-v1.1.md) |
| Type | Decision: where the application tests are logged |
| Trigger | A review of the sync rules after backend v1.1 found that the test log covered only the SQL script (Q-36) |

## Summary

- The tests of the Java application are recorded in the test log [`concurrency-tests.md`](../../db/verification/concurrency-tests.md), in a new section, Application tests.
- The section lists the 7 tests of the test infrastructure, and a table that maps each check of Block D to the Java test that will repeat it (T-17). No such test is written yet.
- The counts of the script (156 checks) and of the application tests are kept apart.

## Changes

| Area | Change |
| --- | --- |
| Test log | New section, Application tests, with the latest `mvnw test` run (7 passed, 0 failed) and a "How to add or change a test" step for it |
| Sync rules | The rows and the checklist for application tests now name the log |
| Backend design | The Tests section points to the log |
| To-do list | Q-36 removed (decided); T-17 asks for each test to be recorded in the log |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the application tests are logged | A section in the existing test log, so that one file shows a design rule, the script check, and the Java test that repeats it | A separate file; only the Tests section of the backend design, which would not show which script check each test repeats |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [concurrency-tests.md](../../db/verification/concurrency-tests.md) | Updated: Application tests, and one step in How to add or change a test |
| [documentation-sync-rules.md](../documentation-sync-rules.md) | Updated: the application tests row and the checklist; the API, environment, and roadmap rules were added earlier the same day |
| [Backend design document](../backend/backend-design.md) | Updated: Tests, and the Change Log |
| [README.md](../../README.md) | Updated: Project Log |
| [project-todo.md](../project-todo.md) | Updated: Q-36 added and removed, T-17 extended |
| Database design, migrations, verification script | Not changed. The script was not re-run, because no check changed |

## Open Items

- Write the Java tests that repeat the checks of Block D (T-17), and fill in the table as each is written.
