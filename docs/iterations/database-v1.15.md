# Database Iteration v1.15

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Database Design](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.15 |
| Date | 2026-11-15 |
| Baseline | [database-v1.14.md](database-v1.14.md) |
| Type | A privilege of the application account. No table, column, or flow changed |
| Trigger | The owner's decision on 2026-11-15 on Q-37, before Phase 3 is merged into main |

## Summary

- The application account no longer has `INSERT` on `roles`. The four roles are fixed, and a new role comes with a migration, so nothing in the application ever inserts one.
- It keeps `UPDATE` on `roles` only because MySQL allows `SELECT ... FOR UPDATE` to an account that may change the table, and Administrator protection locks the `ADMIN` row.

## Changes

| Area | Before (v1.14) | Now |
| --- | --- | --- |
| [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) | `GRANT INSERT, UPDATE ON opsflow.roles` | `GRANT UPDATE ON opsflow.roles`, with the `REVOKE` for a server set up before |
| The roles are fixed (database design) | No `DELETE` on `roles` | Neither `INSERT` nor `DELETE` on `roles` |
| [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1) | 161 checks | 162: `app is refused: INSERT roles (the roles are fixed)` |
| `TestInfrastructureTest` | Used inserts into `roles`, as the application account, as scratch rows for the clean start and the race on a unique key | Uses `users` (the username is unique) instead, and checks that an insert into `roles` is refused |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| `INSERT` on `roles` | Taken away: nothing uses it, and a privilege nobody needs is one a bug or an injection could use | Kept for a future screen of roles: the owner decided on 2026-11-14 that the roles are fixed |
| `UPDATE` on `roles` | Kept, for the row lock | Taken away: `SELECT ... FOR UPDATE` on the `ADMIN` row would be refused, and Administrator protection would lose its second line |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: The roles are fixed, and the Change Log |
| [`restrict-app-privileges.sql`](../../db/admin/restrict-app-privileges.sql) | Updated |
| Local database | `REVOKE INSERT ON opsflow.roles FROM 'opsflow_app'@'localhost'` run as `root` on 2026-11-15, by the assistant with the root password typed by the owner in a masked dialog. `SHOW GRANTS` now lists `UPDATE` alone on `roles` |
| [`run-db-verification.ps1`](../../db/verification/run-db-verification.ps1) | One check added. Run on 2026-11-15: 162 passed, 0 failed |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: Latest run, the counts, and Block C |
| `TestInfrastructureTest` | Changed as above. `mvnw test` on 2026-11-15: 310 passed, 0 failed |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Updated: the per-table privileges, and the `REVOKE` for an older database |
| Migrations, Physical ERD, Simplified ERD | Not changed: no table or column changed |
| [project-todo.md](../project-todo.md) | Q-37 removed as decided |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

None.
