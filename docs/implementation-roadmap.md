# OpsFlow Implementation Roadmap

## Purpose

This file puts the work in [project-todo.md](project-todo.md) into the order in which it can be built, and says why. It answers one question: what is the next step, and what must exist before it.

It does not replace the to-do list. The tasks, their rules, and their details stay in [project-todo.md](project-todo.md), and this file refers to them by ID. When a task is added, finished, or moved, check whether the order here still holds.

## How To Read It

- The work is divided into phases. A phase starts when the one before it is done, unless it says otherwise.
- Every phase ends with something that can be shown working, listed under **Done when**.
- **Decisions needed** are open questions from the to-do list that block the phase. Decide them before the phase starts.

## Overview

| Phase | Goal | Main tasks |
| --- | --- | --- |
| 0 | Done: schema, privileges, backend skeleton, decisions, test infrastructure | |
| 1 | Done: foundations that every module uses | T-13 and T-11 continue with each module |
| 2 | Accounts and login. Done in the backend: login, sign-up, password recovery, and the cleanup job. Left: two rules that wait for the user module | T-43, T-36 |
| 3 | Done in the backend: roles and permissions. The console command that creates an administrator, the permission check of the endpoints, the test of the package dependencies, the four changes one user makes to another, and the user page. The frontend of the phase is built and checked by the owner | |
| 4 | Done: warehouse items, their pictures, and products, with the product page and the first part of the stock page, checked by the owner | |
| 5 | Done: orders, the three stock changes, the two logs with the time filter, the picker, and Deleted items, in the backend and the frontend, checked by the owner | |
| 6 | Done: tickets, their screens and search, the cascades of the user and warehouse modules, and the audit log page, in the backend and the frontend, checked by the owner | |
| 7 | Done: every page of the page design is built, and the last open detail is decided (Q-34). T-27 stays as the rule for each new page | T-27 |
| 8 | Done: Redis and RabbitMQ (Q-07), the Docker Compose deployment (Q-32), a test against the real code for every race (T-17, T-34), and the README and documentation brought up to the finished system. Left: how people are told that the system cancelled their ticket | Q-28 |

## Every Phase

These apply to each module, so they are not repeated below:

1. **Design first.** State the reads, the protection against a concurrent request, and the place in the lock order of every new write path in [database-design.md](database/database-design.md#concurrency-by-module) before writing it (T-34).
2. **Build** the module in its own package, as in [backend-design.md](backend/backend-design.md#layout). Add each new result code to the table in [api-design.md](api/api-design.md).
3. **Test** every rule with a test, and every race with a race test against the MySQL container (T-17, T-34). A rule that every module must keep gets a test that fails the build (see [Rules Checked By Tests](backend/backend-design.md#rules-checked-by-tests)). Keep slow work out of every transaction (see [No slow work inside a transaction](backend/backend-design.md#no-slow-work-inside-a-transaction)).
4. **Grant** the application account the rights for any new table in [restrict-app-privileges.sql](../db/admin/restrict-app-privileges.sql) (T-04).
5. **Record** the iteration as the sync rules require, and update the to-do list.

## Phase 0: Done

- The schema `V1__initial_schema.sql` (16 tables; `V3` added the 17th in Phase 2), applied by Flyway, and the per-table privileges.
- The backend skeleton, and the decisions on the persistence layer (`JdbcTemplate`), transactions (`@Transactional`), the package layout (by feature), authentication (BCrypt and a signed JWT), and the API conventions.
- The test infrastructure: a MySQL 8.4 container, the race runner, and the first 7 tests.

## Phase 1: Done

[backend-v1.3.md](iterations/backend-v1.3.md) and [api-v1.1.md](iterations/api-v1.1.md) record it.

- The base path `/api/v1`, one exception handler that returns Problem Details with a `code`, the cursor codec and the paged-response type, the `Idempotency-Key` check, and the retry component that sits outside the `@Transactional` method.
- Every Java time in UTC: the `Clock` bean, `UtcTime`, and a test that fails when code reads the time zone of the machine.
- The shared declaration of the length limits (`Limits`), checked against the database by a test, and the first operating parameters (the retry and the page size) in application configuration.
- 73 tests, among them a real deadlock and a real lock wait timeout in the MySQL container.
- Added before Phase 2 ([backend-v1.4.md](iterations/backend-v1.4.md)): a request id on every response and log line, and the tests run in continuous integration on every push.

Two tasks do not end here, because they grow with the modules: apply `Limits` to each request type as it is written (T-13), and add each module's parameters to `OpsflowProperties` (T-11).

## Phase 2: Accounts and Login

Everything after this needs to know who is calling.

**Done** ([backend-v1.5.md](iterations/backend-v1.5.md)): the seed migration with the four roles and the SYSTEM identity; login with BCrypt and the signed token; the check of the account on every request; the lockout; the refusal of a login whose password was replaced while it was checked, with its race test; `ACCOUNT_DISABLED` for a disabled account; and security that refuses any request that no rule allows. Also done, in the same iteration: the email codes with their limits (10 requests of one client address in an hour, 200 of the whole system in a day, and no mail for an address that cannot use the code), sign-up, the password reset, the mail component, and the cleanup job (T-29, T-49). The mail service is decided (Q-12): Gmail SMTP with an app password, from an account made for OpsFlow.

**Still to do:**

| Task | What |
| --- | --- |
| T-43 | No way to change the username or the email |
| T-36 | The rules of the SYSTEM identity that belong to the user module. Its login is already refused, and its address gets no code |

**Decisions needed:** none.

**Done when:** a person can sign up with a code, log in, be locked out after repeated failures, reset the password, and get a `401` with an old token after the password changes; a login that checked the old password while a reset committed is refused; and a disabled account gets `ACCOUNT_DISABLED` at login and on its next request.

## Phase 3: Roles and Permissions

The first administrator comes first, because no one can be given a role without one. Permissions come before any business module, so that every endpoint written afterwards is checked from the start.

| Task | What |
| --- | --- |
| T-27 | Done ([backend-v1.6.md](iterations/backend-v1.6.md)): every endpoint is checked against the roles of the caller and refused by default, with a test that fails for an endpoint that declares no permission, and `ACCOUNT_NO_ROLE` for a user with no role. What stays is one line on each new endpoint |
| T-54 | Done ([backend-v1.7.md](iterations/backend-v1.7.md)): the test of the package dependencies, in place before the second module is written |
| T-21, T-19, T-15, T-26, T-23, T-06 | Done ([backend-v1.7.md](iterations/backend-v1.7.md)): replacing a user's roles, disabling, enabling, and soft-deleting, each in one transaction on the steps of a change to a user, with authority over roles and accounts, administrator protection, and their race tests. One part of T-21 moved to Phase 6: returning the tickets of a disabled or deleted user to the pool |
| T-48, T-45 | Done ([backend-v1.7.md](iterations/backend-v1.7.md)): the user page, the first list, paged by key |
| T-65 | Built: one signed-in device per account, decided on 2026-11-15 and built before Phase 4 while the authentication code is fresh ([backend-v1.8.md](iterations/backend-v1.8.md), [frontend-v1.5.md](iterations/frontend-v1.5.md)) |
| T-66 | Done ([database-v1.14.md](iterations/database-v1.14.md)): the time of a delisting is taken after the lock, found by the verification run of 2026-11-15. The Java code of T-39 follows the same rule |
| T-64 | Built ([frontend-v1.5.md](iterations/frontend-v1.5.md)): the frame of the application with the navigation by permission, and the user page with its four changes. The owner checked it against the running backend on 2026-11-15 |

**Done when:** the console command makes an administrator, who can give roles to users and see the list of users but cannot change their own account, while a manager and an operator get `403` where the matrix says so.

## Phase 4: Warehouse Items and Products

Orders need products on sale, so this comes first.

| Task | What |
| --- | --- |
| T-39 | Adding a warehouse item, the catalog, listing, delisting, editing, deleting softly, and the product page |
| T-42 | Item images. Built: the upload checked in the page and by the server, random names, and the pictures on both pages ([backend-v1.9.md](iterations/backend-v1.9.md), [frontend-v1.6.md](iterations/frontend-v1.6.md)) |
| T-41 | The quantity check, starting with the starting quantity of an item |
| T-46 (first part) | The stock page without its quantity changes and logs: the rows, the search, the paging, and adding, editing, and deleting an item, so that a manager has a page to add items on (the owner's decision, 2026-11-15) |

The Order button of the product page waits for Phase 5 (T-10), with the stock changes it shares its locks with. The API, the backend, the Figma design, and the frontend of this phase are built ([api-v1.7.md](iterations/api-v1.7.md), [backend-v1.9.md](iterations/backend-v1.9.md), [frontend-v1.6.md](iterations/frontend-v1.6.md)), and the owner checked them against the running backend on 2026-11-16.

**One part waits.** Deleting an item and delisting a product also cancel the linked tickets. No ticket exists yet, so build them without that step and add the cancellation in Phase 6, where the ticket rules are. Write the reminder in the code and in the to-do item.

**Done when:** a manager adds an item with a generated SKU, lists it with a price (refused with `NO_STOCK` when it has no stock), edits it, delists it, and deletes it, and the product page shows only the listed products.

## Phase 5: Orders and Stock

The core of the system and the hardest part.

| Task | What |
| --- | --- |
| T-10 | The order and inventory flows as specified, with the retry, the idempotency key, and an `order_attempts` row for every attempt |
| T-12 | The order number |
| T-11 | The order parameters: retry attempts, delays, and the maximum quantity |
| T-41 | The quantity check for an order line, a stock-in, a write-off, and a correction |
| T-30 | Manual stock changes, with a race test against order creation and against the deletion of an item |
| T-50 | The stock logs |
| T-46 | The stock page, the warehouse log and the product log, the searchable picker, and the Deleted items module |
| T-17 | The first pass: repeat the concurrency checks of [concurrency-tests.md](../db/verification/concurrency-tests.md) against this code |

**Built so far** (2026-11-16, on `phase-5-backend`): the backend of the whole phase, that is the order flow and the three manual stock changes with their race tests, and the two logs with the time filter, the Deleted items module, and the product picker ([backend-v1.10.md](iterations/backend-v1.10.md), [api-v1.8.md](iterations/api-v1.8.md)). The Figma design was reviewed and the frontend built on 2026-11-16 ([frontend-v1.8.md](iterations/frontend-v1.8.md)), and the owner checked the phase against the running backend the same day. Phase 5 is done.

**Done when:** many threads ordering the same product never take the stock below zero, every attempt leaves its `order_attempts` row, a repeated key adds nothing, and the race tests for stock changes pass.

## Phase 6: Tickets

Tickets link to orders and to items, so they come after both.

| Task | What |
| --- | --- |
| T-14, T-40 | Opening a ticket, by hand and on chosen items, with the limit of 99 and the order and follow-up checks |
| T-12 | The ticket number |
| T-33 | The status rules, the archive, and the jobs that archive tickets |
| T-35, T-67 | Taking, assigning, reassigning, and the two limits, with their races; the permission to assign to an administrator |
| T-21 | Return the tickets of a user to the pool when the user is disabled or soft-deleted |
| T-36 | Exempt the SYSTEM identity from the limits |
| T-39 | Add the ticket cancellation to deleting an item and delisting a product |
| T-44 | The ticket screens and what each role sees, filtered in the backend |
| T-47 | The ticket search |
| T-50 | The ticket logs |
| T-48 | The audit log page, now that every module writes its logs |

**Built so far** (2026-11-16, on `phase-6-backend`): the backend of the whole phase, that is opening a ticket, the eight changes with their limits and races, the archive job, the cascades of the user and warehouse modules, the screens and the search with who sees what, the user picker, and the audit log page ([backend-v1.11.md](iterations/backend-v1.11.md), [api-v1.9.md](iterations/api-v1.9.md)). The Figma design was reviewed and the frontend built on 2026-11-16, on `phase-6-frontend` ([frontend-v1.9.md](iterations/frontend-v1.9.md)): the five screens, the detail with its actions, Create ticket, and the audit log page. The owner clicked through the phase against the running backend on 2026-11-17, and the four changes it asked for were built the same day ([database-v1.17.md](iterations/database-v1.17.md)). Phase 6 is done.

**Done when:** a ticket goes through every allowed status and refuses every other transition, two people cannot take the same ticket, an operator cannot read another person's ticket by its number, and deleting an item cancels its tickets as the SYSTEM identity.

## Phase 7: Frontend

It is built beside the backend and grows one module at a time as each backend part is finished. It started on 2026-11-13 with the account pages, whose backend is done. The stack is React, TypeScript, Vite, and Ant Design, and each page is drawn in Figma before it is built ([frontend-v1.2.md](iterations/frontend-v1.2.md)).

| Task | What |
| --- | --- |
| Q-34 | Done ([frontend-v1.12.md](iterations/frontend-v1.12.md)): every page is drawn and built, and the last open detail is decided: an operator sees only their own order records, and the other roles filter them by who ordered |
| T-27 | Hide the controls the user cannot use |
| T-30 | Done ([frontend-v1.8.md](iterations/frontend-v1.8.md)): the plus and minus for operators, and Save enabled only when the quantity and the reason are filled in |
| T-37 | Done ([frontend-v1.9.md](iterations/frontend-v1.9.md)): the ticket list order, system tickets first in either direction |

**Decisions needed:** the review of each drawn page before it is built. The account pages are reviewed and built.

**Done when:** each page of the page design works against the API for every role.

## Phase 8: Hardening and the Rest

| Task | What |
| --- | --- |
| T-17, T-34 | Done ([backend-v1.14.md](iterations/backend-v1.14.md)): every check of the script and every race of Concurrency by Module has a test against the real code, and the test log matches the code. T-34 stays as the rule for each new write path |
| Q-07, T-08 | Done ([backend-v1.12.md](iterations/backend-v1.12.md)): Redis for the login counts of each client address, and RabbitMQ for the mail queue, with their variables. Idempotency stays in MySQL |
| Q-32 | Done for one machine ([backend-v1.13.md](iterations/backend-v1.13.md)): Docker Compose with nginx, the health check, and the counters; the backup and the way of working in [backend-v1.14.md](iterations/backend-v1.14.md). More than one instance and a server on the internet are a module for later, in [future-ideas.md](future-ideas.md) |
| Q-28 | How the creator and the assignee are told that a ticket was cancelled |
| | Done ([backend-v1.15.md](iterations/backend-v1.15.md)): the README and the documentation brought up to the finished system, for a public repository |

## Order Inside A Phase

Within a phase, build in the order of the tables above unless a task says otherwise. When two tasks depend on each other, the later one in the table is built second.
