# Database Iteration v1.13

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [OpsFlow Database Design](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.13 |
| Date | 2026-11-15 |
| Baseline | [database-v1.12.md](database-v1.12.md) |
| Type | A privilege of the application account, and details of Steps of a change to a user that were fixed when the four changes were built. No table, field, or constraint changed, and there is no migration |
| Trigger | The four changes one user makes to another are built ([backend-v1.7.md](backend-v1.7.md)) |

## Summary

- **The application account has `DELETE` on `user_roles`.** Taking a role away deletes a row. The privilege script grants it, for that table only.
- **The application account keeps `UPDATE` on `roles`**, although it never changes a role: MySQL allows `SELECT ... FOR UPDATE` only to an account that may change the table, and Administrator protection locks the `ADMIN` row. v1.12 said it needed no `UPDATE` there, which was wrong.
- **The refusals of a change to a user have result codes.** New: `USER_CHANGED`, `USER_NOT_ACTIVE`, and `LAST_ADMIN`.
- **The search of the user page is defined**: a part of the username, without regard to case. v1.12 said only that it matches the text typed.
- **Who sees whom on the user page follows the permissions in the code**, which give the same result as the rule written with the roles.
- **The SYSTEM identity is refused in step 3**, with the rows locked, and not in step 1: whether a row is the SYSTEM identity is read from the row.
- **The rules of Authority over roles and accounts are permissions and a mark on the roles** in the code (`ROLE_ASSIGN`, `ROLE_ASSIGN_PROTECTED`, `USER_MANAGE`, `USER_MANAGE_PROTECTED`, and the protected mark), so the service names no role.
- **Returning the tickets of a disabled or soft-deleted user to the pool is not built yet.** The owner decided on 2026-11-15 to add it with the ticket module. The design of step 6 is unchanged.

## Changes

| Area | Before (v1.12) | Now |
| --- | --- | --- |
| Privileges on `user_roles` | `INSERT`. `DELETE` was planned for when the feature is built (T-06) | `INSERT` and `DELETE` |
| Privileges on `roles` | The design said none of `INSERT`, `UPDATE`, `DELETE` was needed. The script granted `INSERT` and `UPDATE` | `UPDATE` is needed for the row lock and stays. No `DELETE`. `INSERT` is still granted and not used (see Open Items) |
| User page, the search | Narrows to the users whose username matches the text typed | Contains the text, without regard to case; spaces around it are ignored, and `%` and `_` are plain characters |
| User page, who sees whom | Written with `ADMIN` and `MANAGER` | The same rule, and a note that the code decides it from the permissions |
| Steps of a change to a user, step 1 | Refuse when the target is the actor or the SYSTEM identity | Refuse when the target is the actor. The SYSTEM identity is refused in step 3, as a user who does not exist |
| Steps of a change to a user | The refusals had no codes | A table of each refusal and its result |
| Authority over roles and accounts | The rules named `ADMIN` and `MANAGER` | The same rules, and a note on how the code holds them: the four permissions and the protected mark of a role |
| Steps of a change to a user, step 6 | Disabling and soft-deleting return the tickets of the user to the pool | The same, with a note that this part is built with the ticket module (T-21) |
| Verification script | Checked that `DELETE` on `user_roles` is refused, and that it works once granted | Checks that `DELETE` on `user_roles` works, and that `DELETE` on `roles` is refused. The number of checks is the same, 161 |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How the `ADMIN` row is locked without the right to change it | Keep `UPDATE` on `roles` for the application account | A lock table of its own with one row: a new table and a migration for something the `roles` row already gives. `GET_LOCK`: it is not released by a rollback and is outside the lock order |
| Where the SYSTEM identity is refused | In step 3, from the locked row | In step 1, by reading the row first without a lock: one more read, for a request that the pages never send |
| What a target that is the SYSTEM identity gets | `NOT_FOUND`, like a user who does not exist | `FORBIDDEN`: the identity is in no list and no picker, so for a caller it does not exist |
| When the tickets of a disabled or deleted user are returned to the pool | With the ticket module, in Phase 6. Until then nothing creates a ticket, so none can be in progress | Now, from the user module: the ticket rules do not exist as code yet |
| Whether step 7 stays although it cannot fail today | Yes | Removing it: only an administrator who is not the target can take `ADMIN` away, so the actor is still one, but that follows from the present roles. A later role that may assign protected roles without being an administrator would reach it |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: Role management, Authority over roles and accounts, Steps of a change to a user, and the Change Log |
| [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Updated: `DELETE` on `user_roles` |
| [environment-setup.md](../environment-setup.md) | Updated: the one line to run on a database where the script ran before. It was run on the owner's local database on 2026-11-15, and the account now has `INSERT` and `DELETE` on `user_roles` |
| [run-db-verification.ps1](../../db/verification/run-db-verification.ps1) | Updated: the two checks named above. **Re-run on 2026-11-15, MySQL 8.4: 161 passed, 0 failed**, started by the assistant with the root password typed by the owner in the masked dialog. The application tests also exercise the privilege: they apply the same privilege script to the MySQL container and replace roles as the application account |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the two entries of Block C, the application tests of the user module, and What is not tested yet |
| [api-design.md](../api/api-design.md) | The three codes, in [api-v1.5.md](api-v1.5.md) |
| Migrations, the Physical ERD, the Simplified ERD, the Business Process Flowchart | Not changed: no table, field, relationship, or flow of the diagrams changed |
| [README.md](../../README.md) | Updated: Project Log |

## Open Items

- Decide whether to take `INSERT` on `roles` away from the application account, now that the roles are fixed. It is harmless and unused; removing it changes the privilege script, the verification script, and needs a `REVOKE` on the local database.
- Returning the tickets of a disabled or soft-deleted user to the pool, with the ticket module (T-21).
