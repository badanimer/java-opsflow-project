# Backend Iteration v1.14

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current backend design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.14 |
| Date | 2026-11-18 |
| Baseline | [backend-v1.13.md](backend-v1.13.md) |
| Type | A read rule, the last race tests, and the way the deployment is worked with and backed up |
| Trigger | The rest of Phase 8. Q-34 decided by the owner on 2026-11-18; T-17 and T-34; the backup and the way of working offered after Q-32. Work on the branch `phase-8-finish` |

## Summary

- **The order records of one's own** (Q-34): the permission `ORDER_VIEW_ALL` for administrators, managers, and viewers; `/order-records` narrows the records to the caller for everyone else, and takes the person filter `userId` for those who hold it.
- **The last races against the real code** (T-17, T-34): two orders of the same items in opposite order never deadlock (D6); a reassignment against a resolution and against a cancellation; the archive job against a manager; a disabling against the person's own release and resolution; and a ticket opened as its product is delisted. Every check of the script and every race of Concurrency by Module now has a test against the code.
- **Development and the deployment, and the backup**: how the two are used together, why the data survives a rebuild, and how the deployment is backed up and restored, checked by running it.
- 7 new tests. The last full run: 459 passed, 0 failed.

## Changes

| Area | Change |
| --- | --- |
| `common.security` | New `Permission.ORDER_VIEW_ALL`, granted by `ADMIN`, `MANAGER`, and `VIEWER` in `Role` |
| `log` | `LogController.orderRecords` takes `userId` and the caller; `LogService.orderRecords` reads everyone's with `ORDER_VIEW_ALL`, otherwise the caller's own, refuses `userId` without it (`FORBIDDEN`) and with `orderNo` (`VALIDATION_FAILED`); `LogRepository.orderRecords` filters on `a.user_id` |
| `user` | The user picker for a filter is open to `ORDER_VIEW_ALL` too |
| Tests | `LogTest.anOperatorReadsOnlyTheirOwnOrderRecordsAndTheOtherRolesFilterByWhoOrdered`; `PermissionTest` checks the new permission; `OrderRaceTest.twoOrdersOfTheSameItemsInOppositeOrderNeverDeadlock`; five new races in `TicketRaceTest` |
| `.gitignore` | `deploy/backup/` |
| Documentation | Backend design: Permissions, Tests, Logs, and under Deployment the new parts Development and the deployment, and Backup and restore |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How D6 shows that no deadlock happened | The counter `opsflow.retry.retries` does not move over 12 rounds, and every order completes | Only counting completed orders, which a deadlock retried by the code would also give |
| The order of the products in D6 | Listed so that the product ids run against the item ids, so the lock order of the stock must come from the item ids in the code | Products and items in the same order, which would pass even if the code sorted by product id only |
| The race of a ticket and a delisting | Both pass; the ticket is cancelled, or was opened after the delisting and stays open, as Concurrency by Module says | Requiring one outcome: the order of two commits is not fixed |
| The default `guest` account of RabbitMQ in `application.yml` | Kept | Removed: the development machine connects to its local RabbitMQ with it and has no `OPSFLOW_RABBITMQ_*` set. RabbitMQ accepts `guest` only from the same machine, and the deployment sets its own account, so the default opens nothing |
| How the backup leaves the container | `mysqldump` into a file in the container, then `docker compose cp` | `>` in PowerShell 5.1, which writes UTF-16 that MySQL cannot read back |

## Verification

| Check | Result |
| --- | --- |
| `mvnw test` | 459 passed, 0 failed (2026-11-18) |
| The backup on the running deployment | The dump held the 17 tables and `flyway_schema_history`, and the item images were copied out |
| The restore | The dump loaded into an empty MySQL 8.4 container: 18 tables, migration version 4, the users there. The container was removed after |
| `db/verification/run-db-verification.ps1` | Not run: the schema, the privileges, and the write flows did not change |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design](../backend/backend-design.md) | Updated: Change Log, Permissions, Tests, Logs, Deployment |
| [Database design](../database/database-design.md) | Updated in [database-v1.19.md](database-v1.19.md) |
| [API design](../api/api-design.md) | Updated in [api-v1.11.md](api-v1.11.md) |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the new tests, D6 written, What is not tested yet emptied, the latest run |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md), [future-ideas.md](../future-ideas.md) | Updated: T-17, Q-34, and Q-32 removed from the to-do; T-34 kept as the rule for new write paths; more than one instance and a server on the internet added as an idea, by the owner's decision |
| [README.md](../../README.md) | Updated: Project Log |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | Not changed: no account, variable, or service changed |

## Open Items

None.
