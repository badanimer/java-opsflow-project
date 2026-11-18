# OpsFlow Project To-Do

## Purpose

This file is the single list of work that is still to be done on OpsFlow. It has two parts:

- **Confirmed**: tasks that were decided and will be done. Each one has a trigger, which says when it becomes due.
- **Potential**: questions and ideas that are not decided yet. Each one says what has to be decided.

It is written for people planning the next step and for AI tools that need to know what is pending. It lists only open work. Finished work is removed from this file, and its history stays in the iteration files in [`docs/iterations`](iterations/).

The rules for keeping this file current are in [documentation-sync-rules.md](documentation-sync-rules.md).

## How To Use This File

- A task is added here when it is confirmed, or when an iteration file or document records it as an open item.
- A task is removed when it is done. Do not mark it as finished and leave it here.
- A potential item moves to Confirmed when a decision is made, and the decision is recorded in the iteration that makes it. If the answer is "no", the item is removed.
- Every item keeps a link to the document that explains it.
- Ideas for features after the program is complete are not tasks. They are kept in [future-ideas.md](future-ideas.md), and an idea is added there only after the owner approves it.

## Confirmed

### Database and migrations

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |
| T-04 | Add a grant for every table that a later migration creates. | With each migration that adds a table | [restrict-app-privileges.sql](../db/admin/restrict-app-privileges.sql) |

### Backend implementation

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |
| T-13 | Use the constants of `Limits` on every text field of every request type, so that over-long input is rejected instead of truncated, and add a constant (and a line in `LimitsMatchSchemaTest`) for each new text field. The shared declaration and the check against the schema are done, and the limits of the two `TEXT` columns are decided: 4,000 characters for `tickets.description` and 2,000 for `tickets.resolution` ([api-v1.9.md](iterations/api-v1.9.md)). | With each request type | [Length limits](backend/backend-design.md#length-limits), [Field Specifications](database/database-design.md#field-specifications) |
| T-27 | Declare who may call each new endpoint, from its row in the Permission Matrix: `@RequiresPermission` with the permission, `@AnyAccount`, or `@Public`. The check itself is built (`EndpointPermissions`): a method that declares nothing is refused, the roles of a user add up, and a test fails the build for an endpoint with no declaration. Which role grants which permission is written only in the enum `Role`; an endpoint and a service name a permission and never a role, and a new action is a new constant of `Permission`. Check the rules that depend on the data in the service, inside the transaction. In the frontend, hide the controls the user cannot use: show a control by its name in `permissions` of `GET /auth/me`, never by a role ([api-v1.4.md](iterations/api-v1.4.md)). | With each module; the frontend part with each page | [Permission Matrix](database/database-design.md#permission-matrix), [Permissions](backend/backend-design.md#permissions) |
| T-34 | Implement the rules of Concurrency by Module as each module is built. State the reads, the protection, and the lock order of any new write path in the design document before building it, and give each of its races a test against the real code, recorded in [concurrency-tests.md](../db/verification/concurrency-tests.md). Every race of the modules built so far has one, and the checks of the script are repeated against the real code (T-17, [backend-v1.14.md](iterations/backend-v1.14.md)). | With each module | [Concurrency by Module](database/database-design.md#concurrency-by-module) |
| T-41 | Give any quantity field added later the rule of Valid quantities, in the backend through `Quantities` and in the page before it is sent, with a range in Operating Parameters. Every quantity so far is checked in both: the starting quantity, a stock-in, a write-off, a correction, and an order line ([backend-v1.10.md](iterations/backend-v1.10.md), [frontend-v1.8.md](iterations/frontend-v1.8.md)). | With each new quantity field | [Valid quantities](database/database-design.md#valid-quantities) |
| T-43 | Give users no way to change the username or the email: no screen and no endpoint. Leave `users.username_changed_at` and `users.email_changed_at` empty. | When the user module is built | [Username and email are fixed](database/database-design.md#username-and-email-are-fixed) |
| T-45 | Page every later list by key, as the user page does (`UserRepository.list`): read `PageQuery.fetchSize()` rows after or before the cursor on the sort key of the list and then the `id`, and let `PageResponse.of` make the cursors. Use `PageQueryFactory.create` for a list in the default order and `createInOrder` for another fixed order. Cover the first page, the last page, and rows inserted between two page reads with a test for each list. The user page is built ([backend-v1.7.md](iterations/backend-v1.7.md)). | With each list | [Home page and paging](database/database-design.md#home-page-and-paging) |

### Frontend and environment

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |

## Potential

These are not decided. Each one says what has to be decided.

### Database design

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
| Q-04 | The initial model has one product per warehouse item and no reservation step. | When to add warehouses, a reservation step with its own table, payment and refund entities, shipment entities, or log partitioning. Each depends on a feature entering the project scope. | [Future Evolution](database/database-design.md#future-evolution) |
| Q-28 | Decided: a ticket that links several products is cancelled when any one of them is delisted, or when any linked item is deleted, because a person opens such a ticket and can open a new one. The cancelled ticket shows a message such as "Widget has been delisted, please open the ticket again". There is no notification system yet, so nothing tells the person who opened the ticket or the one holding it that it was cancelled. | How the creator and the assignee are reminded: a mark on the cancelled area, a to-do list (see [future-ideas.md](future-ideas.md)), or a notification module. To be decided when the to-do list or the notification system is designed. | [Listing and delisting](database/database-design.md#listing-and-delisting) |
### Documents and diagrams

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |

### Technology and security

| ID | Question | What has to be decided | Details |
| --- | --- | --- | --- |
