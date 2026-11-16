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
| T-08 | Add the RabbitMQ variables to the environment-variable configuration, and read them from the environment like the MySQL ones. | When RabbitMQ is introduced | [ENVIRONMENT_SETUP.md](../ENVIRONMENT_SETUP.md) |
| T-13 | Use the constants of `Limits` on every text field of every request type, so that over-long input is rejected instead of truncated, and add a constant (and a line in `LimitsMatchSchemaTest`) for each new text field. The shared declaration and the check against the schema are done, and the limits of the two `TEXT` columns are decided: 4,000 characters for `tickets.description` and 2,000 for `tickets.resolution` ([api-v1.9.md](iterations/api-v1.9.md)). | With each request type | [Length limits](backend/backend-design.md#length-limits), [Field Specifications](database/database-design.md#field-specifications) |
| T-17 | Repeat the concurrency checks against the real application code, including its retry logic, connection pool, and transaction handling. Record each test in the Application tests section of [concurrency-tests.md](../db/verification/concurrency-tests.md), in the row of the script check it repeats. D1 to D5 and the ticket races are done ([backend-v1.10.md](iterations/backend-v1.10.md), [backend-v1.11.md](iterations/backend-v1.11.md)); still to do: two orders that lock the same items in opposite order (D6) against the real code. | Phase 8 | [Concurrency and privilege verification](database/database-design.md#concurrency-and-privilege-verification) |
| T-27 | Declare who may call each new endpoint, from its row in the Permission Matrix: `@RequiresPermission` with the permission, `@AnyAccount`, or `@Public`. The check itself is built (`EndpointPermissions`): a method that declares nothing is refused, the roles of a user add up, and a test fails the build for an endpoint with no declaration. Which role grants which permission is written only in the enum `Role`; an endpoint and a service name a permission and never a role, and a new action is a new constant of `Permission`. Check the rules that depend on the data in the service, inside the transaction. In the frontend, hide the controls the user cannot use: show a control by its name in `permissions` of `GET /auth/me`, never by a role ([api-v1.4.md](iterations/api-v1.4.md)). | With each module; the frontend part with each page | [Permission Matrix](database/database-design.md#permission-matrix), [Permissions](backend/backend-design.md#permissions) |
| T-34 | Implement the rules of Concurrency by Module as each module is built, and repeat the order and inventory style of verification against the real code for users and roles, and authentication. The tickets are done ([backend-v1.11.md](iterations/backend-v1.11.md)). State the reads, the protection, and the lock order of any new write path in the design document before building it. | With each module; the checks when T-17 is done | [Concurrency by Module](database/database-design.md#concurrency-by-module) |
| T-40 | Build the frontend of opening a ticket on items: the Create ticket button on the ticket pool screen; the list of the items that are not deleted (`GET /items`), searched by name, with 0 to 20 chosen; the type, title, and description with the limits of the page design; the order number and follow-up fields; the result of each refusal, `ITEM_CHANGED` naming the items; and the cancelled ticket of a deleted or delisted item with the message built from `cancelCause`. The backend is built ([backend-v1.11.md](iterations/backend-v1.11.md)). | Phase 6 frontend | [Create ticket](frontend/page-design.md#create-ticket), [Creating a ticket on items](database/database-design.md#creating-a-ticket-on-items) |
| T-41 | Give any quantity field added later the rule of Valid quantities, in the backend through `Quantities` and in the page before it is sent, with a range in Operating Parameters. Every quantity so far is checked in both: the starting quantity, a stock-in, a write-off, a correction, and an order line ([backend-v1.10.md](iterations/backend-v1.10.md), [frontend-v1.8.md](iterations/frontend-v1.8.md)). | With each new quantity field | [Valid quantities](database/database-design.md#valid-quantities) |
| T-43 | Give users no way to change the username or the email: no screen and no endpoint. Leave `users.username_changed_at` and `users.email_changed_at` empty. | When the user module is built | [Username and email are fixed](database/database-design.md#username-and-email-are-fixed) |
| T-44 | Build the ticket screens in the frontend: the pool, in progress, resolved, archive, and cancelled screens with their status labels and the rows of the API, the detail view with its buttons by status and permission, and an operator's view without archive and search. The filtering by user id is built in the backend, with a test that an operator cannot read another person's ticket ([backend-v1.11.md](iterations/backend-v1.11.md)). | Phase 6 frontend | [Ticket screens](frontend/page-design.md#ticket-screens), [Ticket screens and who sees what](database/database-design.md#ticket-screens-and-who-sees-what) |
| T-45 | Page every later list by key, as the user page does (`UserRepository.list`): read `PageQuery.fetchSize()` rows after or before the cursor on the sort key of the list and then the `id`, and let `PageResponse.of` make the cursors. Use `PageQueryFactory.create` for a list in the default order and `createInOrder` for another fixed order. Cover the first page, the last page, and rows inserted between two page reads with a test for each list. The user page is built ([backend-v1.7.md](iterations/backend-v1.7.md)). | With each list | [Home page and paging](database/database-design.md#home-page-and-paging) |
| T-47 | Build the ticket search in the frontend for `ADMIN`, `MANAGER`, and `VIEWER`: the kind, creator, assignee, and creation days on each screen, and the ticket number or one order number alone, with the user picker (`GET /user-options?use=filter`) and the assignee picker (`use=assignee`). The backend is built ([backend-v1.11.md](iterations/backend-v1.11.md)). | Phase 6 frontend | [Ticket search](database/database-design.md#ticket-search), [The user picker](database/database-design.md#the-user-picker) |
| T-48 | Build the audit log page in the frontend: a menu of seven areas, the list of the chosen area with its three filters (the action, the time filter, and the user from the user picker or System), 10 to a page, for `ADMIN` and `MANAGER`; each row laid out from its labels. The backend is built: `GET /audit-records` and `GET /audit-order-records` ([backend-v1.11.md](iterations/backend-v1.11.md)). | Phase 6 frontend | [Audit log page](database/database-design.md#audit-log-page), [Logs](api/api-design.md#logs) |

### Frontend and environment

| ID | Task | When it is due | Details |
| --- | --- | --- | --- |
| T-37 | Implement the ticket list order on the screen (the screens themselves are T-44): every role that can view tickets can choose the direction, newest first by default, and tickets with `source` `SYSTEM` always come before tickets with `source` `MANUAL` in either direction, ordered by the time the list is about (creation, archive, or cancellation). The choice is a screen setting and is not stored. | When the frontend starts | [Ticket list order](database/database-design.md#ticket-list-order) |

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
| Q-07 | Redis and RabbitMQ are installed but not used. Requests for an email code are now limited per client address and for the whole system, counted in MySQL (see Email codes), so that part is closed. Login attempts are still limited only per account: anyone who knows a username can lock that account. The client address is the address of the connection, so behind a reverse proxy every request would have the address of the proxy. | When to introduce Redis (for idempotency, and for a limit on logins per client address) and RabbitMQ (for asynchronous processing), and how the client address is read behind a reverse proxy, which belongs with the deployment plan (Q-32). | [README.md](../README.md#implementation-direction), [Email codes](database/database-design.md#login-security-and-account-recovery) |
| Q-34 | The page design is a first version. | The visual design and navigation of the pages that are not drawn yet (the account pages have a draft), and the details listed under Open Details in the page design (the filter pickers, the empty states and error messages, whether the stock page shows if an item is listed, and small screens). To be decided when the UI design starts. | [page-design.md](frontend/page-design.md#open-details) |
| Q-32 | The tests run in continuous integration on every push. There is still no deployment plan, and the application is designed to run as one instance. | How the application is built and run outside the development machine. With it: counters for retries and `BUSY` answers and a health check (for example Spring Boot Actuator), and whether more than one instance runs, which needs a lock for the scheduled jobs. | [Continuous integration](backend/backend-design.md#continuous-integration), [Scheduled Jobs](backend/backend-design.md#scheduled-jobs) |
