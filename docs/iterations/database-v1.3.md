# Database Iteration v1.3

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.3 |
| Date | 2026-10-21 |
| Baseline | v1.2, see [database-v1.2.md](database-v1.2.md) |
| Type | Design rules (no schema change) |
| Table count | 12 (unchanged) |
| Trigger | A review of how `user_roles` behaves with soft-deleted users and with removed roles |

## Summary

Version 1.3 adds two rules around role grants and changes no table, field, or constraint. The foreign keys on `user_roles` do not read the soft-delete flag, and the table keeps no history, so two gaps existed: a role could be granted to a soft-deleted user, and revoking a grant erased the only trace that it had existed. The first gap is closed by a service-layer rule. The second is closed by logging every grant and revocation in `operation_logs`.

## Changes

### Role grants to soft-deleted users

- The design document now states that the database accepts a grant to a soft-deleted user, because a foreign key only checks that the row exists.
- The service layer must refuse a grant to a user whose `deleted` is true. The database cannot enforce this, so the rule is written into Identity and Access and into Initial Constraints.

### Role grant and revocation logs

- Every grant and revocation writes one `operation_logs` row in the same transaction as the change to `user_roles`. If the log insert fails, the change rolls back with it.
- Actions are `ROLE_GRANTED` and `ROLE_REVOKED`, with `target_type` `USER` and `target_id` set to the affected user. A grant fills `after_data` and a revocation fills `before_data`, both with `roleId` and `roleCode`.
- The log stores `roleCode` so it stays readable after a role is removed. Removing a role requires revoking every grant of it first, and each revocation is logged, so the history of who held the role is kept.
- The application account already has `INSERT` on `operation_logs`, so no privilege changes.

### Document alignment

- A note under Design Diagrams said the Business Process Flowchart still showed the earlier flow. v1.2 had already updated it, so the note was corrected.

## Decisions Made In This Iteration

| Decision | Choice | Alternative considered |
| --- | --- | --- |
| Refusing grants to soft-deleted users | Service-layer rule, documented | A database trigger, which would hide the rule from application code |
| Keeping the history of grants | Log every grant and revocation in `operation_logs` | A dedicated grant-history table, or soft-deleting roles |
| Dedicated grant-history table and soft-deleted roles | Deferred until the permission module is designed | Adding either now, which changes the schema and both ERDs before the permission rules are known |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated |
| [Initial migration](../../db/migration/V1__initial_schema.sql) | Not changed; no schema change |
| [Physical ERD](https://www.figma.com/board/GNiX9p2D1tTfKvqPs8L9DQ/OpsFlow-Initial-Physical-ERD?node-id=0-1) | Not changed; no table or field changed |
| [Simplified ERD](https://www.figma.com/board/FTesM5Ue6WpNV6GcipdfK9/OpsFlow-Database-ER-Diagram?node-id=0-1) | Not changed; no table or relationship changed |
| [Verification scripts](../../db/verification/) | Not re-run; the schema, the privileges, and the fixtures did not change, and both new rules are application behavior |

## Open Items

- Implement both rules in the permission module when the backend exists: refuse grants to soft-deleted users, and write the `ROLE_GRANTED` and `ROLE_REVOKED` log rows in the same transaction as the change to `user_roles`.
- Decide, when the permission module is designed, whether roles need a status instead of physical deletion, and whether grants need a dedicated history table.
- The open items of [database-v1.2.md](database-v1.2.md) still apply.
