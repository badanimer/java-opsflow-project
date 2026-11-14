# Backend Iteration v1.6

## Purpose

This file records one iteration of the OpsFlow backend: what changed, why, and where the updated artifacts live. It is written for people reviewing project history and for AI tools that need to locate the current design and understand how it evolved.

The authoritative description is [OpsFlow Backend](../backend/backend-design.md). This file only summarizes the delta.

## Iteration Info

| Item | Value |
| --- | --- |
| Version | 1.6 |
| Date | 2026-11-15 |
| Baseline | [backend-v1.5.md](backend-v1.5.md) |
| Type | The first part of the permission module (Phase 3): the check of every endpoint against the roles of the caller. No migration, no new environment variable, and no new endpoint |
| Trigger | The Permission Matrix was reviewed in [database-v1.12.md](database-v1.12.md), so it can be implemented (T-27) |

## Summary

- **A controller method declares who may call it**, with exactly one of `@Public`, `@AnyAccount`, and `@RequiresPermission`. `@RequiresPermission` is one row of the Permission Matrix, written on the method.
- **An endpoint names a permission, never a role.** `Permission` is the actions of the matrix, and the enum `Role` lists what each role grants. That list is the only place that ties a role to a permission, so a new role is a migration and one constant, and no endpoint changes.
- **`EndpointPermissions` decides every request** from that declaration. It replaces the list of paths in `SecurityConfig`.
- **Refuse by default.** A method that declares nothing is refused for everyone, an administrator included, and so is a request with no controller method.
- **`GET /auth/me` returns the permissions of the user**, so that the frontend names no role either ([api-v1.4.md](api-v1.4.md)).
- **The roles of a user add up**: a user passes when any one of their roles grants the permission.
- **A user with no role gets `ACCOUNT_NO_ROLE`** on an endpoint that needs a permission (T-22), and a user whose roles do not grant it gets `FORBIDDEN`.
- **The four roles are the enum `Role`**, checked against the `roles` table by a test.
- **A test fails the build** for an endpoint of the application that does not declare exactly once.

## Changes

| Area | Before (v1.5) | Now |
| --- | --- | --- |
| Who may call an endpoint | A list of paths in `SecurityConfig`: four for everyone and one for a logged-in user. Everything else was refused | Each controller method declares it. `SecurityConfig` hands every request to `EndpointPermissions` |
| The five endpoints of `AuthController` | Named in `SecurityConfig` | `@Public` on login, the email codes, sign-up, and the password reset, and `@AnyAccount` on `GET /auth/me` |
| Roles | Strings in `CurrentUser.roles()`, read by nothing but `GET /auth/me` | The enum `Role` with the permissions of each role, the enum `Permission` (25 actions of the matrix), and `CurrentUser.can(Permission)` and `holds(Role)`. The endpoint check asks `can`, and `GET /auth/me` returns `CurrentUser.permissions()` |
| A user with no role | Could call only `GET /auth/me`; any other request was `FORBIDDEN`, like every request | `ACCOUNT_NO_ROLE` on an endpoint that needs a permission |
| The rule that every endpoint declares its permission | The refusal was built; the test over the endpoints was planned | Built: `EveryEndpointDeclaresWhoMayCallItTest` |
| Tests | 222 | 244 |

## Decisions Made

| Decision | Chosen | Not chosen |
| --- | --- | --- |
| Where an endpoint says who may call it | On the controller method, as an annotation | A list of paths in `SecurityConfig`: a new endpoint is in one file and its rule in another, and a path written twice can differ. Spring's `@PreAuthorize` with an expression in a string: a wrong role name is found only when the endpoint is called |
| How the declaration is enforced | One `AuthorizationManager` in the filter chain that looks up the controller method of the request | Method security around the controller: a method with no annotation would then be open, not closed |
| A method that declares nothing | Refused for everyone | Open to every logged-in user: forgetting the line would open the endpoint |
| What an endpoint names | A permission, `@RequiresPermission(Permission.AUDIT_VIEW)`, with the roles that grant it listed once, in `Role`. The owner asked for it on 2026-11-15, so that a role can be added later without touching the endpoints | The roles themselves on each endpoint, `@AllowedRoles({ Role.ADMIN, Role.MANAGER })`, which was built first in this iteration and replaced before it was committed: a new role that may do much of what a manager does would change every endpoint that names `MANAGER` |
| Which side holds the list | Each role lists its permissions, so the list reads as a column of the matrix and a new role is one constant | Each permission lists its roles: a new role would touch every permission |
| Which permissions exist now | All 25 of the reviewed matrix, also those of the modules that are not built, so that the matrix is in the code in one piece and the tests can use real permissions | Only those of the built endpoints: none of them needs a permission yet |
| Where the list is kept | In the code | In tables of the database: the roles and what they may do are fixed and change only with a backend change, and a list in the code is checked by the compiler and the tests |
| Whether a higher role passes by itself | No. Each role lists every permission it grants, and a test checks that a manager has those of an operator and an administrator those of a manager | A ranking of the roles: `VIEWER` reads things that `OPERATOR` does not, so the roles are not a ladder |
| The answer for a user with no role on an endpoint that declares nothing | `FORBIDDEN`, as for everyone | `ACCOUNT_NO_ROLE`: the endpoint would not open with a role either |
| A path that exists with another HTTP method | Refused by the permission check, as before | Letting it through to be answered `METHOD_NOT_ALLOWED`: the check would have to allow a request it knows no endpoint for |
| Which controllers the test over the endpoints checks | Those compiled from `src/main`, found by where the class was loaded from | A list of the test controllers to leave out: a new one would have to be added to it |

## Affected Artifacts

| Artifact | Status |
| --- | --- |
| [Backend design document](../backend/backend-design.md) | Updated: Shared Code, Rules Checked By Tests, Tests, Authentication, the new section Permissions, and the Change Log |
| Code | Added: `Permission`, `Role`, `Public`, `AnyAccount`, `RequiresPermission` in `common.security`, and `EndpointPermissions` in `auth`. Changed: `CurrentUser`, `SecurityConfig`, `SecurityErrorHandler`, `AuthController` |
| Tests | Added: `EndpointPermissionTest` (13) with `PermissionProbeController`, `EveryEndpointDeclaresWhoMayCallItTest` (2), `RoleTest` (3), and `PermissionTest` (4). `mvnw test` on 2026-11-15: **244 passed, 0 failed** |
| [Test log](../../db/verification/concurrency-tests.md) | Updated: the latest run, `RoleTest` and `PermissionTest` under Shared code, and the new section Roles and permissions |
| [api-design.md](../api/api-design.md) | The meanings of `FORBIDDEN` and `ACCOUNT_NO_ROLE` are worded as the code now answers. The new field `permissions` of `GET /auth/me` is recorded in [api-v1.4.md](api-v1.4.md) |
| [database-design.md](../database/database-design.md) | One sentence under the Permission Matrix says where the permissions live. No rule changed, so there is no database iteration |
| [project-todo.md](../project-todo.md), [implementation-roadmap.md](../implementation-roadmap.md) | Updated: T-22 removed as done, and T-19 and T-27 narrowed to what is left |
| [README.md](../../README.md) | Updated: Project Log |
| [ENVIRONMENT_SETUP.md](../../ENVIRONMENT_SETUP.md), migrations, [restrict-app-privileges.sql](../../db/admin/restrict-app-privileges.sql) | Not changed: no variable, table, or privilege is new. The verification script was not re-run |

## Open Items

- Declare the permission of each new endpoint with its module, from its row in the Permission Matrix, and hide the controls a user cannot use in the frontend (T-27).
- The test of the package dependencies (T-54).
- The four changes to a user, with their races (T-21, T-15, T-26, T-23, T-06, T-50), and the rest of T-19: a soft-deleted user is set to `DISABLED`.
- The user list (T-48, T-45).
