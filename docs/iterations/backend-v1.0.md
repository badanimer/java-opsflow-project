# Backend Iteration v1.0

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.0 |
| Date | 2026-11-03 |
| Baseline | None: the first backend iteration |
| Type | New area: the project skeleton |
| Trigger | The database design is complete, and the owner started the backend on the branch `feature/backend-skeleton` |

## Summary

- A Maven project was generated with Spring Initializr into `backend/`, with the root package `com.opsflow`.
- The dependencies are the web layer, validation, JDBC, Flyway, and the MySQL driver. No persistence layer was chosen yet.
- The configuration reads the four database variables from the environment and sets the UTC parameters on the JDBC URL.
- `V1__initial_schema.sql` was moved (not copied) from `db/migration` to `backend/src/main/resources/db/migration`, and every link and the verification script were updated.
- The code compiles. The application was not started, so Flyway has not applied V1 yet.

## Changes

| Area | Change |
| --- | --- |
| Project | `backend/` with `pom.xml`, the Maven wrapper, and the application class |
| Configuration | `application.yml` with the environment variables and the UTC parameters |
| Migration | Moved to `backend/src/main/resources/db/migration` (T-03 done) |
| Verification script | Reads the migration from its new path |
| Documents | New area document, sync-map row, README, and links |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where the backend lives | `backend/` under the repository root, so that `frontend/` can sit beside it | The repository root |
| Spring Boot version | 4.0.9, the oldest release Spring Initializr still offers | 3.5.x, which the owner first chose but the generator no longer offers; building it by hand would run on a line without support |
| Root package | `com.opsflow` | `io.github.badanimer.opsflow` |
| Persistence layer | Left open (T-51) | JPA or MyBatis chosen at once |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | New |
| [documentation-sync-rules.md](../documentation-sync-rules.md) | Updated: a Backend row in the Sync Map, and the migration link |
| [README.md](../../README.md) | Updated: Documentation Structure and Project Log |
| [project-todo.md](../project-todo.md) | Updated: T-03 and T-07 removed, T-08 and T-09 narrowed, T-51 added |
| [run-db-verification.ps1](../../db/verification/run-db-verification.ps1) | Updated: the path of the migration. Not re-run after the move |
| Database design, ERDs | Only the links and the migration location changed. No table or flow changed |

## Open Items

- Choose the persistence layer (T-51).

## First Run

On 2026-11-04 the application was started for the first time. Flyway applied V1 to `opsflow` (version 1, success), giving 16 tables plus `flyway_schema_history`. `db/admin/restrict-app-privileges.sql` was then run once as `root`, and the grants of `opsflow_app` match the script. T-01 and T-02 are done.

The first start failed with "Public Key Retrieval is not allowed", because the JDBC URL had `useSSL=false` and MySQL 8.4 accounts use `caching_sha2_password`. `useSSL=false` was removed from the URL (also in `ENVIRONMENT_SETUP.md`), so the driver encrypts the connection by default.
