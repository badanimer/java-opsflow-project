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
| 2 | Accounts and login | T-05, T-20, T-28, T-29, T-49, T-43, T-36 |
| 3 | Roles and permissions | T-24, T-27, T-19, T-15, T-21, T-26, T-23, T-22, T-06, T-48, T-45 |
| 4 | Warehouse items and products | T-39, T-42, T-41 |
| 5 | Orders and stock | T-10, T-12, T-41, T-30, T-46, T-50 |
| 6 | Tickets | T-14, T-40, T-33, T-35, T-44, T-47, T-48, T-50 |
| 7 | Frontend | T-18, Q-34, T-22, T-27, T-30, T-37 |
| 8 | Hardening and the rest | T-17, T-34, Q-07, T-08, Q-32, Q-28 |

## Every Phase

These apply to each module, so they are not repeated below:

1. **Design first.** State the reads, the protection against a concurrent request, and the place in the lock order of every new write path in [database-design.md](database/database-design.md#concurrency-by-module) before writing it (T-34).
2. **Build** the module in its own package, as in [backend-design.md](backend/backend-design.md#layout). Add each new result code to the table in [api-design.md](api/api-design.md).
3. **Test** every rule with a test, and every race with a race test against the MySQL container (T-17, T-34).
4. **Grant** the application account the rights for any new table in [restrict-app-privileges.sql](../db/admin/restrict-app-privileges.sql) (T-04).
5. **Record** the iteration as the sync rules require, and update the to-do list.

## Phase 0: Done

- The schema `V1__initial_schema.sql` (16 tables), applied by Flyway, and the per-table privileges.
- The backend skeleton, and the decisions on the persistence layer (`JdbcTemplate`), transactions (`@Transactional`), the package layout (by feature), authentication (BCrypt and a signed JWT), and the API conventions.
- The test infrastructure: a MySQL 8.4 container, the race runner, and the first 7 tests.

## Phase 1: Done

[backend-v1.3.md](iterations/backend-v1.3.md) and [api-v1.1.md](iterations/api-v1.1.md) record it.

- The base path `/api/v1`, one exception handler that returns Problem Details with a `code`, the cursor codec and the paged-response type, the `Idempotency-Key` check, and the retry component that sits outside the `@Transactional` method.
- Every Java time in UTC: the `Clock` bean, `UtcTime`, and a test that fails when code reads the time zone of the machine.
- The shared declaration of the length limits (`Limits`), checked against the database by a test, and the first operating parameters (the retry and the page size) in application configuration.
- 73 tests, among them a real deadlock and a real lock wait timeout in the MySQL container.

Two tasks do not end here, because they grow with the modules: apply `Limits` to each request type as it is written (T-13), and add each module's parameters to `OpsflowProperties` (T-11).

## Phase 2: Accounts and Login

Everything after this needs to know who is calling.

| Task | What |
| --- | --- |
| T-05 | The seed migration: the four roles and the SYSTEM identity. Update the test base so that emptying the tables does not remove the seed |
| T-20 | Login with BCrypt, the signed token, and the check of the account on every request |
| T-28 | The login lockout |
| T-29 | Sign-up and password recovery with email codes, and the password rule |
| T-49 | The job that deletes expired email codes |
| T-43 | No way to change the username or the email |
| T-36 | The rules of the SYSTEM identity that belong to login and the user module |

**Decisions needed:** Q-12, the mail service. T-29 can be built behind a small mail interface with a fake in the tests, so the choice of a provider only has to be made before the code is used for real.

**Done when:** a person can sign up with a code, log in, be locked out after repeated failures, reset the password, and get a `401` with an old token after the password changes.

## Phase 3: Roles and Permissions

The first administrator comes first, because no one can be given a role without one. Permissions come before any business module, so that every endpoint written afterwards is checked from the start.

| Task | What |
| --- | --- |
| T-24 | The console command that creates the first administrator |
| T-27 | Review the Permission Matrix, then check every endpoint against the roles of the caller |
| T-19, T-15 | A role counts only for a usable account; role grants are refused to other accounts and logged |
| T-21 | Role management for `ADMIN`, and replacing a user's roles in one transaction |
| T-26 | Authority over roles and accounts |
| T-23 | Administrator protection, with a race test of two administrators removing each other |
| T-22 | The backend part of the pending page: the account state and the distinct error codes |
| T-06 | Grant `DELETE` on `user_roles` and `roles` |
| T-48 | The user list and role management through the API. This is the first list, so build the paging with it (T-45) |
| T-50 | The user and account logs and the role logs |

**Done when:** the console command makes an administrator, who can create roles, give them to users, and see the list of users, while a manager and an operator get `403` where the matrix says so.

## Phase 4: Warehouse Items and Products

Orders need products on sale, so this comes first.

| Task | What |
| --- | --- |
| T-39 | Adding a warehouse item, the catalog, listing, delisting, editing, deleting softly, and the product page |
| T-42 | Item images |
| T-41 | The quantity check, starting with the starting quantity of an item |

**One part waits.** Deleting an item and delisting a product also cancel the linked tickets. No ticket exists yet, so build them without that step and add the cancellation in Phase 6, where the ticket rules are. Write the reminder in the code and in the to-do item.

**Done when:** a manager adds an item with a generated SKU, lists it with a price (refused with `NO_STOCK` when it has no stock), edits it, delists it, and deletes it, and the product page shows only the listed products.

## Phase 5: Orders and Stock

The core of the system and the hardest part.

| Task | What |
| --- | --- |
| T-10 | The order and inventory flows as specified, with the retry, the idempotency key, and an `order_attempts` row for every attempt |
| T-12 | The order number |
| T-11 | The order parameters: retry attempts, delays, and the maximum quantity |
| T-41 | The quantity check for an order line, a stock-in, a write-off, and a direct set |
| T-30 | Manual stock changes, with a race test against order creation and against the deletion of an item |
| T-50 | The stock logs |
| T-46 | The stock page, the warehouse log and the product log, the searchable picker, and the Deleted items module |
| T-17 | The first pass: repeat the concurrency checks of [concurrency-tests.md](../db/verification/concurrency-tests.md) against this code |

**Done when:** many threads ordering the same product never take the stock below zero, every attempt leaves its `order_attempts` row, a repeated key adds nothing, and the race tests for stock changes pass.

## Phase 6: Tickets

Tickets link to orders and to items, so they come after both.

| Task | What |
| --- | --- |
| T-14, T-40 | Opening a ticket, by hand and on chosen items, with the limit of 99 and the order and follow-up checks |
| T-12 | The ticket number |
| T-33 | The status rules, the archive, and the jobs that archive tickets |
| T-35 | Taking, assigning, reassigning, and the two limits, with their races |
| T-36 | Exempt the SYSTEM identity from the limits |
| T-39 | Add the ticket cancellation to deleting an item and delisting a product |
| T-44 | The ticket screens and what each role sees, filtered in the backend |
| T-47 | The ticket search |
| T-50 | The ticket logs |
| T-48 | The audit log page, now that every module writes its logs |

**Done when:** a ticket goes through every allowed status and refuses every other transition, two people cannot take the same ticket, an operator cannot read another person's ticket by its number, and deleting an item cancels its tickets as the SYSTEM identity.

## Phase 7: Frontend

It can start earlier, after Phase 3, and grow one module at a time as each backend part is finished. The choice below decides that.

| Task | What |
| --- | --- |
| T-18 | Choose Vue or React, and a scaffolding tool |
| Q-34 | The visual design and the open details of the [page design](frontend/page-design.md) |
| T-22 | The pending page |
| T-27 | Hide the controls the user cannot use |
| T-30 | The plus and minus for operators, and Save enabled only when the quantity and the reason are filled in |
| T-37 | The ticket list order |

**Decisions needed:** T-18, and whether to build the frontend after the backend or beside it.

**Done when:** each page of the page design works against the API for every role.

## Phase 8: Hardening and the Rest

| Task | What |
| --- | --- |
| T-17, T-34 | Repeat the concurrency checks for tickets, roles, and authentication against the real code, and check that the test log matches the code |
| Q-07, T-08 | Redis for idempotency and rate limiting, and RabbitMQ for asynchronous work, if they are brought in. T-08 follows with the RabbitMQ variables |
| Q-32 | Continuous integration and the deployment plan |
| Q-28 | How the creator and the assignee are told that a ticket was cancelled |
| | The README and the documentation brought up to the finished system |

## Order Inside A Phase

Within a phase, build in the order of the tables above unless a task says otherwise. When two tasks depend on each other, the later one in the table is built second.
