# Database Iteration v1.12

## Purpose

This file records one iteration of the OpsFlow database design: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative design is [Database Structure and Business Flow](../database/database-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.12 |
| Date | 2026-11-14, extended on 2026-11-15 with the review of the filters |
| Baseline | v1.11, see [database-v1.11.md](database-v1.11.md) |
| Type | A review of the Permission Matrix and of the rules it rests on. Rules of the service layer only. No table, column, constraint, index, migration, or privilege changed |
| Table count | 17, unchanged |
| Trigger | The review of the Permission Matrix with the owner, which is the first step of Phase 3 (roles and permissions) |

## Summary

- **The four roles are fixed.** `ADMIN`, `MANAGER`, `OPERATOR`, and `VIEWER` are the only roles. The program has no way to create, update, or delete a role. A new role comes only with a backend change: a migration and the code for its permissions.
- **A user may hold several roles, and they add up.** A user may do whatever any one of their roles allows, and where a role limits what is seen, the widest view wins. This was not stated before.
- **Nobody changes their own account.** No user, an administrator included, may disable or soft-delete their own account or change their own roles.
- **Setting the quantity directly is now called Correction, and an `OPERATOR` may use it.** It is for a stock count that differs from the number in the system. It is recorded as before, as `ADJUST_IN` or `ADJUST_OUT`, with a required reason.
- **A user picker** returns only ids and usernames. It serves the creator and assignee filters of the ticket search, and the choice of an assignee.
- **A manager cannot give a ticket to an administrator.** An operator, a manager, or an administrator may still hold a ticket, a manager may take one, and managers may assign tickets to each other. Only an administrator assigns or reassigns a ticket to an administrator.
- **The filters of every screen were reviewed one by one** (extended on 2026-11-15). The stock page gets a search box for the name. One time filter is the template for every list filtered by time: a start day to an end day, or a start day until now, with newest first or oldest first. The own log of one product or item gets it. The user filter of the audit log page reads the user picker and adds a choice for System. The user page gets a search box for the username and role buttons, with one for users who have no role. The Deleted items module, the ticket search, and the filters of the audit log page are confirmed as they were.
- **The steps of a change to a user are written down** (2026-11-15), before the permission module is built: replacing the roles, disabling, enabling, and soft-deleting lock the user rows of the actor and of the target, read the roles of both under the locks, and compare the target's roles with the ones the actor saw.
- **The other working defaults of the matrix are confirmed**: only `ADMIN` and `MANAGER` archive a ticket or give it to someone else, and an `OPERATOR` cancels and resolves only their own tickets.

## Changes

| Area | Before (v1.11) | Now |
| --- | --- | --- |
| Role definitions | `ADMIN` could create a role, update its `name` and `description`, and delete it, through the backend | No one can. The four seeded roles are fixed |
| Deleting a role | Removed every holder's grant, logged each one, and was covered by Administrator protection | Does not exist |
| Role management logs | `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED` | Removed from the design. `ROLE_GRANTED` and `ROLE_REVOKED` stay |
| Privileges planned for `roles` | `DELETE` on `roles`, to be granted with role deletion (T-06) | Not needed. T-06 keeps `DELETE` on `user_roles` only |
| Several roles on one user | Possible in the tables, with no rule for the permissions | The permissions are the union |
| A user's own account | A manager could not change their own account. Nothing was said for an administrator | Nobody can disable or soft-delete their own account or change their own roles |
| Administrator protection, covered operations | Included deleting a role | Disabling or soft-deleting a user, revoking `ADMIN`, and replacing roles so that `ADMIN` is removed |
| Typing the new stock quantity | "Set the quantity directly", for `ADMIN` and `MANAGER` | "Correction", for `ADMIN`, `MANAGER`, and `OPERATOR` |
| The inventory screen | An operator saw a plus and a minus. A manager or an administrator also had a field for the new quantity | All three roles choose plus, minus, or Correction |
| Usernames for the ticket filters and for choosing an assignee | Not stated. The user page is closed to `VIEWER`, and a manager's list leaves out managers and administrators | The user picker: ids and usernames only, never the SYSTEM identity. Every user for the filters (`ADMIN`, `MANAGER`, `VIEWER`), and the eligible assignees for assigning (`ADMIN`, `MANAGER`) |
| Assigning or reassigning a ticket to an administrator | A `MANAGER` or an `ADMIN` could | Only an `ADMIN` can. The assignee picker of a manager leaves administrators out |
| Stock page | No search | A search box for the name, and the rows ordered by name |
| Filtering by time | A range of days, from one day to another, in the logs, the ticket search, and the audit page. Newest first, switchable only on the ticket screens | One template, The time filter: a start day to an end day (today at the latest), or a start day until now, with newest first or oldest first, everywhere |
| The own log of one product or item | No filter | The time filter |
| The user filter of the audit log page | By `operator_id`, with no word on where the names come from | From the user picker, which includes disabled and soft-deleted users, or the choice System |
| Narrowing the user page | No search and no filter | A search box for the username, and role buttons of which several can be chosen: the roles the viewer may manage, and No role |
| Locks of a change to a user | The target's user row, then the `ADMIN` row in `roles` for every covered operation | The user rows of the actor and of the target in ascending id, then the `ADMIN` row only when the target holds `ADMIN` |
| The actor's authority | Checked in the same transaction, from a plain read | Read under a lock on the actor's own row, so an actor demoted a moment earlier is refused |
| Two replacements of one user's roles | The second waited and then set its own list | The second is refused when the roles are no longer the ones its actor saw |
| A replacement on a disabled user | "The user must be a usable account", while a revocation was always allowed | Adding a role needs a usable account. A list that only removes roles is accepted |
| Concurrency by Module, Users and roles | Four races, one of them with the deletion of a role | Six: the row of a deleted role is gone, and added are the actor who loses the authority while acting, a role taken away while a request is running, and a status change during a replacement |
| User page of an `ADMIN` | Every user except the SYSTEM identity | The same, and their own row has no control to change it |
| Permission Matrix | A first version with working defaults | Reviewed. The two rows about granting roles are one row, and rows are added for a person's own account and for the fixed roles |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How many roles a user holds | Several, with the permissions as the union. The owner first chose one role for a user and then kept the list | One role for a user, with a unique key on `user_roles.user_id`: it needs a migration and takes away a combination such as an operator who also reads everything. The strictest of the roles: a second role would then never add anything |
| Who manages role definitions | No one. The four roles are fixed, and a new role is a backend change | An administrator creating, renaming, and deleting roles (v1.4): a role made that way would have no permissions until the code is changed, so the feature could not do anything useful |
| A person's own account | Nobody can disable it, delete it, or change its roles. Another administrator does it | Allowing it while one other administrator is left: an administrator could lock themselves out by a slip |
| Who makes a correction | `OPERATOR` as well. A count that differs from the system is found by the person in the warehouse | `MANAGER` and `ADMIN` only: the operator would have to work out the difference and book a stock in or a write-off |
| The name | Correction | Set the quantity directly |
| Where usernames come from | A picker that returns ids and usernames only | Opening the user page to `VIEWER`: it shows emails, statuses, and roles. Typing a full username with no list |
| Which users the filter picker returns | Every user, disabled and soft-deleted ones included, because their tickets and records remain. Confirmed by the owner on 2026-11-15 | Usable accounts only: the tickets of a person who has left could not be filtered |
| One filter strategy or one for each screen | Each screen was reviewed by itself, and only the time filter is shared | One strategy for every screen: the screens ask different questions |
| The role buttons of a manager | `OPERATOR`, `VIEWER`, and No role: the roles of the users a manager can manage. Confirmed by the owner on 2026-11-15 | A `MANAGER` button as well: a manager's list never shows a manager, so the button would always find nothing |
| The Deleted items module | Kept: it is the only way to find the records of a soft-deleted item by its name or SKU | Dropping it: the pickers do not offer a deleted item, so its records could only be found by paging through all records |
| Whether the actor's row is locked | Yes, with the target's, in ascending id. Confirmed by the owner on 2026-11-15 | Reading the actor's roles without a lock: a manager demoted at the same moment could still finish a change |
| Two replacements at once | The second is refused when the roles changed since its actor saw them. Confirmed by the owner on 2026-11-15 | The last one wins: a role that one manager has just given is removed by another who never saw it |
| The lock on the `ADMIN` row and the count | Kept as a second line, though the locks on the user rows already keep one administrator | Dropping them: the rule would then rest on one mechanism, and a later path that forgets the actor's lock would break it unnoticed |
| A role taken away while a request is running | That one request may finish, except in the paths that lock a user row. Confirmed by the owner on 2026-11-15 | Locking the user row in every write: every order of a person would wait for every other action of that person |
| The time filter | Days, in two forms, with a choice of direction | Times of day: nobody searches a log to the minute. A fixed newest-first order: a person following a history reads it oldest first |
| Who may be given a ticket | An operator or a manager by a manager, and an administrator only by an administrator. Anyone of the three may take one from the pool | Operators only: a manager could not take an urgent ticket. A manager assigning to an administrator (v1.11): a manager has no authority over an administrator anywhere else |
| Archiving and giving a ticket to someone else | `ADMIN` and `MANAGER` only, as before | An operator passing a ticket to a colleague: the operator releases it to the pool |
| An operator's cancel and resolve | Only their own tickets, as before | Any ticket |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Database design document](../database/database-design.md) | Updated: Manual stock changes, Who may take a ticket, Categories, Role management, Authority over roles and accounts, Administrator protection, Steps of a change to a user (new), Role grant and revocation logs, Stock logs, The time filter (new), Stock page, Logs, Ticket search, The user picker (new), User page, Audit log page, Permission Matrix, Concurrency by Module, Initial Constraints, and the Change Log. The section Role management logs is removed |
| [page-design.md](../frontend/page-design.md) | Updated in [frontend-v1.4.md](frontend-v1.4.md) |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated: T-06, T-21, T-23, T-26, T-27, T-30, T-41, T-47, T-48, and Phase 3. T-55 removed: the owner read the results of the two continuous integration jobs, and they passed |
| [environment-setup.md](../environment-setup.md) | Updated: the planned grant is `DELETE` on `user_roles` only |
| [Test log](../../db/verification/concurrency-tests.md) | What is not tested yet: "direct set" is now "correction", and the row of Users and roles lists the races as they are now. No check changed |
| [README.md](../../README.md) | Updated: Project Log |
| [api-design.md](../api/api-design.md) | Not changed: no path and no result code is defined here. They come when the module is built |
| Migrations, [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql), [verification script](../../db/verification/run-db-verification.ps1) and fixtures | Not changed, and the script was not re-run: no schema, privilege, or order and inventory flow changed. Who may make a correction is a permission of the service layer |
| [Seed migration `V2`](../../backend/src/main/resources/db/migration/V2__seed_roles_and_system_identity.sql) | Not changed, because it is applied. The description it gives the `ADMIN` role still mentions role definitions. It is a text in the `roles` row that no screen shows |
| Application code | Not changed. `AuditAction` still has `ROLE_CREATED`, `ROLE_UPDATED`, and `ROLE_DELETED`, which nothing writes. They are removed with T-21 |
| Physical ERD, Simplified ERD, Business Process Flowchart | Not changed: no table, field, relationship, or flow in them changed |

## Open Items

- Build the permission module as reviewed: T-27, T-19, T-15, T-21, T-26, T-23, T-22, T-06, T-48, T-45, T-54.
- Remove the three unused role actions from `AuditAction` (T-21).
- Build the user picker with the ticket search (T-47).
- Build the correction for all three roles (T-30).
- Build the search of the stock page and the time filter (T-46), and the user filter of the audit page (T-48).
- Build the search and the role buttons of the user page (T-48).
- The result codes of the refusals ("user changed, please reload", the last administrator) are named in [api-design.md](../api/api-design.md) when the module is built.
