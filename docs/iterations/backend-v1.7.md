# Backend Iteration v1.7

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.7 |
| Date | 2026-11-15 |
| Baseline | [backend-v1.6.md](backend-v1.6.md) |
| Type | Two parts. First, a rule checked by a test: the dependencies between the packages (T-54), with one new test dependency. Second, the `user` module with the user page (T-48, T-45) and the four changes one user makes to another: replacing the roles, disabling, enabling, and soft-deleting (T-21, T-15, T-19, T-23, T-26, T-06, and the account rows of T-50). No migration and no new environment variable; one new privilege of the application account |
| Trigger | The user module is the second module, so the rules between modules are checked from its first class on. The steps of a change to a user were confirmed in [database-v1.12.md](database-v1.12.md) |

## Summary

- **`PackageDependencyRulesTest` fails the build** when the shared code in `common` depends on a module, when a controller reads the database itself, or when a module uses the repository of another module. It reads the compiled classes of `src/main` with ArchUnit, and each rule is shown to fail on a made-up application in the test package `rulefixture`.
- **The `user` module**, with four endpoints ([api-v1.5.md](api-v1.5.md)): `PUT /users/{id}/roles` sets the roles of a user to a list, `POST /users/{id}/disable` and `POST /users/{id}/enable` change the status, and `DELETE /users/{id}` deletes softly. Each is one transaction.
- **The user page, the first list**: `GET /users`, ordered by username and paged by key, with a search for a part of the username and the role buttons. Who sees whom follows the permissions of the caller.
- **A status change starts from one status**: disabling from `ACTIVE` and enabling from `DISABLED`, read under the lock, and a request that finds the other is refused with `USER_CHANGED`. A soft deletion sets `deleted` and `DISABLED` and keeps the row, the roles, the username, and the email.
- **Not built: returning the tickets of a disabled or deleted user to the pool.** The owner decided on 2026-11-15 to add it with the ticket module.
- **The authority is decided in the transaction.** It locks the user rows of the actor and of the target in ascending id and reads the status and the roles of both again, so an actor who was demoted a moment ago is refused.
- **The request carries the roles the actor saw**, and is refused with `USER_CHANGED` when they are no longer the roles of the user.
- **The service names no role to decide what is allowed.** Two new permissions, `ROLE_ASSIGN_PROTECTED` and `USER_MANAGE_PROTECTED`, and a mark on the roles, `Role.isProtected`, hold the rule that only an administrator changes the roles or the status of a user who holds `ADMIN` or `MANAGER`, or gives those roles.
- **Administrator protection**: the lock on the `ADMIN` row and the count afterwards.
- **Each role added or removed, and each change of status, writes its log row** in the same transaction. The three unused actions `ROLE_CREATED`, `ROLE_UPDATED`, and `ROLE_DELETED` are gone from `AuditAction`.
- **Race tests**: two administrators who remove or disable each other, two changes to one user, the same grant twice, an actor who is demoted or disabled while acting, a grant racing a disabling, a disabling racing a deletion, and a password reset racing a deletion.

## Changes

| Area | Before (v1.6) | Now |
| --- | --- | --- |
| The three rules of the layout | Written in the Layout section, and planned as a test | Checked by `PackageDependencyRulesTest` |
| Test dependencies | Spring Boot test starters and Testcontainers | Also `com.tngtech.archunit:archunit` 1.4.1, in the test scope |
| Modules | `auth` | `auth` and `user` |
| Endpoints | The five of `AuthController` | Also the five of `UserController` |
| Paging | `PageQueryFactory.create`, whose default order is `desc` | Also `createInOrder`, for a list whose fixed order is another one |
| Permissions | 25 | 27: `ROLE_ASSIGN_PROTECTED` and `USER_MANAGE_PROTECTED`, granted to `ADMIN` only |
| `Role` | The permissions of each role | Also `isProtected()`: `ADMIN` and `MANAGER` |
| Result codes | | New: `USER_CHANGED`, `USER_NOT_ACTIVE`, `LAST_ADMIN` |
| `AuditAction` | Had `ROLE_CREATED`, `ROLE_UPDATED`, and `ROLE_DELETED`, which nothing writes since the roles are fixed | Removed. The database has no row with them |
| Privileges of the application account | `INSERT` on `user_roles` | `INSERT` and `DELETE` on `user_roles` ([database-v1.13.md](database-v1.13.md)) |
| Tests | 244 | 301 |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| How the dependencies between packages are read | From the compiled classes, with ArchUnit | A search of the `import` lines in the source, as `UtcSourceRuleTest` does for the time zone: it misses a class used without an import, a wildcard import, and a fully qualified name |
| Which part of ArchUnit | The core library, called from an ordinary JUnit test | Its JUnit runner: one more piece that must follow the JUnit version of Spring Boot |
| What a repository is | A class whose name ends in `Repository`, or that carries `@Repository` | Only the annotation: a repository written without it would be missed |
| What a module is | The package directly under `com.opsflow`. `common` is not a module | A list of module names in the test: a new module would have to be added to it |
| What a controller may not use | `org.springframework.jdbc`, `java.sql`, and `javax.sql` | Only `JdbcTemplate`: `NamedParameterJdbcTemplate` or a `DataSource` would pass |
| How to know that a rule can fail | A made-up application in the tests, in the package `rulefixture` outside `com.opsflow`, that breaks each rule once | Trusting the rule: with one module, the rule about the repository of another module could not fail, and would pass whether or not it works |
| Where the authority of the actor comes from | The roles read in the transaction, with the row of the actor locked | The roles the request arrived with: a manager demoted a moment before would finish a change with an authority that is gone |
| How the rule about `ADMIN` and `MANAGER` is written | A permission, `ROLE_ASSIGN_PROTECTED`, and `Role.isProtected()` | `if the actor holds ADMIN` in the service: a later role that may appoint managers would need the service changed. This follows the owner's wish of 2026-11-15 that a role can be added without touching the rest |
| Where the SQL of users and roles lives | `UserRepository` in the `user` module, with its own statements | The statements of `AuthRepository`: a module does not use the repository of another module, and the test of the packages refuses it |
| One class or several for the changes to a user | One, `UserChangeWriter`, whose first three steps are one method for all four changes | A class for each change: the locks and the checks of the actor would be written four times |
| A role in the list that the user already holds | Left alone: the same row, and no log | Delete all and insert all: every replacement would log every role, and the time a role was granted would be lost |
| A role code named twice | Counted once | A validation error: the meaning is clear |
| Who sees whom on the user page | Decided from the permissions: a caller who may change users with a protected role sees them all, their own account included; any other sees only the users with no protected role, not their own account | `if ADMIN` and `if MANAGER` in the service, against the wish that a role can be added without touching the rest |
| How the search matches | A part of the username, without regard to case, with `%`, `_`, and `!` escaped | The start of the username only: a person often remembers a part of a name. A pattern the caller writes: nobody needs one for a small list |
| How the roles of a row are read | A `GROUP_CONCAT` subquery in the query of the page | A second query for the roles of the ids of the page: two queries where one does |
| How a fixed order other than `desc` is paged | A second method of `PageQueryFactory`, `createInOrder` | Turning the order around in the service after `create`: the query would say one order and the list another |
| A disabling that finds the user disabled | Refused with `USER_CHANGED` and the current status | `200` as if it had worked: a second log row would say that the status changed, or none would, and the person would not learn that someone else acted |
| A deletion of a disabled user | Allowed, and the log keeps the status it found | Only an active user: a disabled account could then never be removed |
| The roles of a deleted user | Kept, like the row | Removed with a `ROLE_REVOKED` row each: the roles of an account that is not usable count for nothing, and the history reads better with them |
| Administrator protection for a deletion of a disabled administrator | Not applied: a disabled administrator is not a usable one, so nothing is lost | The lock and the count for every deletion of a holder of `ADMIN` |
| Returning the tickets of a disabled or deleted user | Left for the ticket module, on the owner's decision | Writing the ticket rows from the user module now: the ticket rules do not exist as code yet, the SQL would move later, and until tickets exist there is nothing to return |
| How the race with a password reset is tested | The test of the user module calls the real reset transaction of the auth module | A copy of the reset in the test: it would test the copy |
| The count of step 7, which cannot be zero today | Kept, and the count itself tested | Removed: see [database-v1.13.md](database-v1.13.md) |
| How the order "demotion first" is tested | A second connection holds the row of the actor, and the test commits the demotion when the change is seen to wait | Only the race: in ten rounds the manager always won, so the refusal was never reached |
| How a test sends a request that arrived before its sender was changed | It calls `UserService` with the id of the actor | HTTP: the token check reads the roles for each request, so over HTTP the endpoint refuses first and the transaction is never reached |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Layout, Rules Checked By Tests, Tests, Permissions, the new section Users, and the Change Log |
| Code | Added: `UserController`, `UserService`, `UserChangeWriter`, and `UserRepository` in `user`. Changed: `Permission`, `Role`, `ErrorCode`, `AuditAction` |
| `backend/pom.xml` | The test dependency `archunit` and the property `archunit.version` |
| Tests | Added: `PackageDependencyRulesTest` (7) with the classes of `rulefixture`, `UserRolesTest` (17), `UserRolesRaceTest` (5), `UserStatusTest` (11), `UserStatusRaceTest` (6), and `UserListTest` (10), with `UserIntegrationSupport`; one more test in `PageQueryFactoryTest`. `mvnw test` on 2026-11-15: **301 passed, 0 failed** |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the latest run, `PackageDependencyRulesTest` under Shared code, the tests of the user module under Roles and permissions, Block C, and What is not tested yet |
| [api-design.md](../api/api-design.md) | The endpoint and the three codes, in [api-v1.5.md](api-v1.5.md) |
| [database-design.md](../database/database-design.md), [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql), [run-db-verification.ps1](../../db/verification/run-db-verification.ps1), [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md) | The privilege and the details of the steps, in [database-v1.13.md](database-v1.13.md). The verification script was changed and re-run on 2026-11-15: **161 passed, 0 failed** |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | T-54, T-06, T-15, T-19, T-23, and T-26 removed as done; T-21 narrowed to returning the tickets and moved to the ticket module; T-36 and T-50 narrowed; T-48 narrowed to the audit log page and T-45 to the later lists; Q-37 added |
| [README.md](../../README.md) | Updated: Project Log |
| Frontend | Not changed. The user page is not built |

## Open Items

- Returning the tickets of a disabled or soft-deleted user to the pool, with the ticket module (T-21).
- The rules of the SYSTEM identity that are left: the lists, the pickers, and the ticket limits (T-36). No way to change the username or the email (T-43): none is built.
- Whether the application account keeps `INSERT` on `roles` (Q-37).
